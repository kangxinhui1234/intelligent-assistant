package com.kxh.aiagent.ops.tool;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.stream.Stream;

/**
 * 本地代码扫描工具 (L1 只读)。
 * <p>
 * 主力使用场景:基于告警栈帧,直达本地仓库读取源码上下文。
 * <p>
 * 安全约束:
 * - service 必须命中配置白名单
 * - 任何文件访问都强制 normalize 后校验在 repo 根之下,防路径穿越
 * - 单文件读取上限 1MB, 单次返回行数上限 200
 * - 禁止读 .git/.idea/target/node_modules/build/dist 等
 */
@Component
@ConfigurationProperties(prefix = "ops.code")
public class LocalCodeTool {

    private static final Logger log = LoggerFactory.getLogger(LocalCodeTool.class);
    private static final ObjectMapper mapper = new ObjectMapper();
    private static final long MAX_FILE_BYTES = 1024L * 1024L;
    private static final int MAX_RETURN_LINES = 200;
    private static final Set<String> FORBIDDEN_DIRS = Set.of(
            ".git", ".idea", ".vscode", "target", "build", "dist",
            "node_modules", "out", ".gradle", ".mvn", "bin"
    );
    private static final Set<String> TEXT_EXTS = Set.of(
            ".java", ".kt", ".groovy", ".scala",
            ".py", ".go", ".js", ".ts", ".jsx", ".tsx",
            ".xml", ".yaml", ".yml", ".json", ".properties",
            ".sql", ".html", ".css", ".sh", ".md", ".txt"
    );

    private List<RepoConfig> repos = new ArrayList<>();

    public List<RepoConfig> getRepos() { return repos; }
    public void setRepos(List<RepoConfig> repos) { this.repos = repos; }

    private Map<String, RepoConfig> repoMap = new HashMap<>();

    @PostConstruct
    public void init() {
        for (RepoConfig r : repos) {
            if (r.getName() == null || r.getPath() == null) continue;
            Path p = Paths.get(r.getPath()).toAbsolutePath().normalize();
            if (!Files.isDirectory(p)) {
                log.warn("LocalCodeTool: repo 路径不存在 name={} path={}", r.getName(), p);
                continue;
            }
            r.setNormalizedPath(p);
            repoMap.put(r.getName(), r);
            log.info("LocalCodeTool: 注册 repo name={} path={} pkgPrefix={}",
                    r.getName(), p, r.getPackagePrefix());
        }
        if (repoMap.isEmpty()) {
            log.warn("LocalCodeTool: 未配置任何本地代码仓库,所有调用会返回 service not configured");
        }
    }

    // ==================== Tools ====================

    @Tool(description = """
            按栈帧定位并读取本地源码。
            优先用此工具:输入服务名、类全限定名(如 com.x.y.Foo)、行号,
            自动通过 package-prefix 反推文件路径,直接读取该行附近代码。
            如果该服务未配置本地仓库,返回 not_configured。""")
    public String readSourceByStackFrame(
            @ToolParam(description = "服务名,需匹配 ops.code.repos 配置") String service,
            @ToolParam(description = "类全限定名,如 com.yiittou.fengine.biz.admin.service.chat.handler.LiteStoryBoardSplitHandler") String className,
            @ToolParam(description = "行号") int lineNo,
            @ToolParam(description = "上下文行数(单边),默认 20") int contextLines) {
        RepoConfig repo = repoMap.get(service);
        if (repo == null) return notConfigured(service);

        Path file = guessFilePath(repo, className);
        if (file == null) {
            return jsonError("class_path_not_resolved",
                    "无法通过 package-prefix 推断文件路径,packagePrefix=" + repo.getPackagePrefix() + " class=" + className);
        }
        if (!Files.isRegularFile(file)) {
            return jsonError("file_not_found", "文件未找到: " + file);
        }
        int ctx = contextLines <= 0 ? 20 : Math.min(contextLines, 50);
        return readFileLines(repo, file, lineNo, ctx, "stack_frame");
    }

    @Tool(description = """
            搜索类/方法/关键词定义位置。
            ripgrep 风格全文搜索,返回最多 30 个匹配,每条带文件路径和行号。""")
    public String grepInService(
            @ToolParam(description = "服务名") String service,
            @ToolParam(description = "搜索关键词或类/方法名") String keyword) {
        RepoConfig repo = repoMap.get(service);
        if (repo == null) return notConfigured(service);
        if (keyword == null || keyword.isBlank()) return jsonError("invalid_keyword", "关键词不能为空");

        ObjectNode root = mapper.createObjectNode();
        root.put("strategy", "grep");
        root.put("service", service);
        root.put("keyword", keyword);
        ArrayNode matches = root.putArray("matches");

        int[] count = {0};
        try (Stream<Path> stream = Files.walk(repo.getNormalizedPath())) {
            stream.filter(this::isTextFile)
                    .filter(p -> !inForbiddenDir(repo, p))
                    .takeWhile(p -> count[0] < 30)
                    .forEach(p -> grepFile(p, keyword, matches, repo, count));
        } catch (IOException e) {
            return jsonError("walk_failed", e.getMessage());
        }
        root.put("count", count[0]);
        return root.toString();
    }

    @Tool(description = """
            读取指定文件的指定行附近代码。
            file 是相对于 repo 根的相对路径,如 src/main/java/com/x/Foo.java""")
    public String readSourceAround(
            @ToolParam(description = "服务名") String service,
            @ToolParam(description = "相对 repo 根的文件路径") String file,
            @ToolParam(description = "中心行号") int lineNo,
            @ToolParam(description = "上下文行数(单边),默认 20") int contextLines) {
        RepoConfig repo = repoMap.get(service);
        if (repo == null) return notConfigured(service);

        Path target = repo.getNormalizedPath().resolve(file).normalize();
        if (!target.startsWith(repo.getNormalizedPath())) {
            return jsonError("path_traversal", "文件路径必须位于 repo 内");
        }
        if (!Files.isRegularFile(target)) {
            return jsonError("file_not_found", "文件未找到: " + target);
        }
        int ctx = contextLines <= 0 ? 20 : Math.min(contextLines, 50);
        return readFileLines(repo, target, lineNo, ctx, "read");
    }

    @Tool(description = """
            列出服务下最近 N 小时修改的源码文件(基于文件 mtime)。
            可作为"最近改动"的粗略信号,但 git pull 会重置 mtime,不完全可靠。""")
    public String recentlyModifiedFiles(
            @ToolParam(description = "服务名") String service,
            @ToolParam(description = "最近多少小时") int hours) {
        RepoConfig repo = repoMap.get(service);
        if (repo == null) return notConfigured(service);
        if (hours <= 0 || hours > 720) hours = 24;
        long threshold = System.currentTimeMillis() - hours * 3600_000L;

        ObjectNode root = mapper.createObjectNode();
        root.put("strategy", "recently_modified");
        root.put("service", service);
        root.put("withinHours", hours);
        ArrayNode arr = root.putArray("files");
        int[] count = {0};
        try (Stream<Path> stream = Files.walk(repo.getNormalizedPath())) {
            stream.filter(this::isTextFile)
                    .filter(p -> !inForbiddenDir(repo, p))
                    .forEach(p -> {
                        if (count[0] >= 30) return;
                        try {
                            BasicFileAttributes attrs = Files.readAttributes(p, BasicFileAttributes.class);
                            if (attrs.lastModifiedTime().toMillis() >= threshold) {
                                ObjectNode item = arr.addObject();
                                item.put("path", repo.getNormalizedPath().relativize(p).toString().replace('\\', '/'));
                                item.put("modifiedAt", attrs.lastModifiedTime().toString());
                                item.put("sizeBytes", attrs.size());
                                count[0]++;
                            }
                        } catch (IOException ignored) {}
                    });
        } catch (IOException e) {
            return jsonError("walk_failed", e.getMessage());
        }
        root.put("count", count[0]);
        return root.toString();
    }

    @Tool(description = "列出当前所有可用的服务(已配置本地代码仓库)及其元信息")
    public String listAvailableServices() {
        ObjectNode root = mapper.createObjectNode();
        ArrayNode arr = root.putArray("services");
        for (RepoConfig r : repoMap.values()) {
            ObjectNode item = arr.addObject();
            item.put("name", r.getName());
            item.put("path", r.getNormalizedPath().toString());
            item.put("packagePrefix", r.getPackagePrefix() == null ? "" : r.getPackagePrefix());
        }
        root.put("count", repoMap.size());
        return root.toString();
    }

    // ==================== helpers ====================

    private Path guessFilePath(RepoConfig repo, String className) {
        if (className == null || className.isBlank()) return null;
        String cls = className.contains("$") ? className.substring(0, className.indexOf('$')) : className;
        String relative = cls.replace('.', '/') + ".java";
        // 尝试常见 Java 源码根路径
        String[] roots = {"src/main/java/", "src/test/java/", "src/main/kotlin/", ""};
        for (String r : roots) {
            Path p = repo.getNormalizedPath().resolve(r + relative).normalize();
            if (p.startsWith(repo.getNormalizedPath()) && Files.isRegularFile(p)) {
                return p;
            }
        }
        return null;
    }

    private String readFileLines(RepoConfig repo, Path file, int centerLine, int ctxLines, String strategy) {
        try {
            long size = Files.size(file);
            if (size > MAX_FILE_BYTES) {
                return jsonError("file_too_large", "文件超过 1MB: " + size + " bytes");
            }
            List<String> all = Files.readAllLines(file, StandardCharsets.UTF_8);
            int total = all.size();
            int from = Math.max(1, centerLine - ctxLines);
            int to = Math.min(total, centerLine + ctxLines);
            // 防止 LLM 看到超大段
            if (to - from + 1 > MAX_RETURN_LINES) to = from + MAX_RETURN_LINES - 1;

            ObjectNode root = mapper.createObjectNode();
            root.put("strategy", strategy);
            root.put("file", repo.getNormalizedPath().relativize(file).toString().replace('\\', '/'));
            root.put("centerLine", centerLine);
            root.put("from", from);
            root.put("to", to);
            root.put("totalLines", total);
            ArrayNode lines = root.putArray("lines");
            for (int i = from; i <= to; i++) {
                ObjectNode ln = lines.addObject();
                ln.put("n", i);
                ln.put("text", all.get(i - 1));
            }
            return root.toString();
        } catch (IOException e) {
            return jsonError("read_failed", e.getMessage());
        }
    }

    private void grepFile(Path file, String keyword, ArrayNode matches, RepoConfig repo, int[] count) {
        if (count[0] >= 30) return;
        try {
            long size = Files.size(file);
            if (size > MAX_FILE_BYTES) return;
            List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
            for (int i = 0; i < lines.size(); i++) {
                if (count[0] >= 30) return;
                String line = lines.get(i);
                if (line.contains(keyword)) {
                    ObjectNode m = matches.addObject();
                    m.put("file", repo.getNormalizedPath().relativize(file).toString().replace('\\', '/'));
                    m.put("line", i + 1);
                    String text = line.length() > 200 ? line.substring(0, 200) + "..." : line;
                    m.put("text", text);
                    count[0]++;
                }
            }
        } catch (IOException ignored) {}
    }

    private boolean isTextFile(Path p) {
        if (!Files.isRegularFile(p)) return false;
        String name = p.getFileName().toString().toLowerCase();
        int dot = name.lastIndexOf('.');
        if (dot < 0) return false;
        return TEXT_EXTS.contains(name.substring(dot));
    }

    private boolean inForbiddenDir(RepoConfig repo, Path p) {
        Path rel = repo.getNormalizedPath().relativize(p);
        for (int i = 0; i < rel.getNameCount(); i++) {
            if (FORBIDDEN_DIRS.contains(rel.getName(i).toString())) return true;
        }
        return false;
    }

    private String notConfigured(String service) {
        return "{\"error\":\"service_not_configured\",\"service\":\""
                + (service == null ? "" : service)
                + "\",\"hint\":\"在 application.yml 配置 ops.code.repos 添加该服务的本地仓库路径\"}";
    }

    private String jsonError(String code, String message) {
        ObjectNode root = mapper.createObjectNode();
        root.put("error", code);
        root.put("message", message);
        return root.toString();
    }

    // ==================== Config Model ====================

    public static class RepoConfig {
        private String name;
        private String path;
        private String packagePrefix;
        private transient Path normalizedPath;

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getPath() { return path; }
        public void setPath(String path) { this.path = path; }
        public String getPackagePrefix() { return packagePrefix; }
        public void setPackagePrefix(String packagePrefix) { this.packagePrefix = packagePrefix; }
        public Path getNormalizedPath() { return normalizedPath; }
        public void setNormalizedPath(Path normalizedPath) { this.normalizedPath = normalizedPath; }
    }
}
