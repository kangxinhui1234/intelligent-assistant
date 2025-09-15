import { marked } from 'marked'

// 配置marked选项
marked.setOptions({
  breaks: true, // 支持换行
  gfm: true,    // 支持GitHub风格的Markdown
})

/**
 * 格式化消息内容，处理JSON、Markdown等特殊格式
 */
export function formatMessageContent(content: string): string {
  if (!content) return content

  let formatted = content

  console.log('原始内容:', content)
  
  // 处理工具调用结果
  formatted = formatToolCallResults(formatted)
  console.log('工具调用结果处理后:', formatted)
  
  // 处理JSON数据
  formatted = formatJsonData(formatted)
  console.log('JSON数据处理后:', formatted)
  
  // 处理Markdown格式
  formatted = formatMarkdown(formatted)
  console.log('Markdown处理后:', formatted)
  
  return formatted
}

/**
 * 格式化工具调用结果
 */
function formatToolCallResults(content: string): string {
  console.log('开始处理工具调用结果:', content)
  
  // 匹配已经格式化的工具调用结果 - 修复正则表达式
  const formattedPattern = /🔧 \*\*([^*]+)\*\*[\s\S]*?\[(.+?)\]/gs
  let formatted = content.replace(formattedPattern, (match, toolName, result) => {
    console.log('匹配到已格式化的工具调用:', toolName, result)
    const formattedToolName = `🔧 **${toolName}**`
    let formattedResult = `[${result}]`
    
    // 特殊处理POI数据
    if (toolName.includes('around_search') || toolName.includes('text_search')) {
      formattedResult = formatPoiData(formattedResult)
    } else if (toolName.includes('maps_geo')) {
      // 处理地理编码结果
      formattedResult = formatGeoData(formattedResult)
    } else {
      // 其他工具结果进行JSON格式化
      formattedResult = formatJsonData(formattedResult)
    }
    
    return `<div class="tool-result">${formattedToolName}\n\n${formattedResult}</div>\n\n`
  })
  
  // 如果没有匹配到，尝试匹配更宽松的格式
  if (formatted === content) {
    console.log('尝试匹配更宽松的格式')
    const loosePattern = /🔧 \*\*([^*]+)\*\*[\s\S]*?(\[.*?\])/gs
    formatted = content.replace(loosePattern, (match, toolName, result) => {
      console.log('匹配到宽松格式工具调用:', toolName, result)
      const formattedToolName = `🔧 **${toolName}**`
      let formattedResult = result
      
      // 特殊处理POI数据
      if (toolName.includes('around_search') || toolName.includes('text_search')) {
        formattedResult = formatPoiData(formattedResult)
      } else if (toolName.includes('maps_geo')) {
        // 处理地理编码结果
        formattedResult = formatGeoData(formattedResult)
      } else {
        // 其他工具结果进行JSON格式化
        formattedResult = formatJsonData(formattedResult)
      }
      
      return `<div class="tool-result">${formattedToolName}\n\n${formattedResult}</div>\n\n`
    })
  }
  
  // 如果还是没有匹配到，尝试匹配包含 **maps_geo** 的格式
  if (formatted === content) {
    console.log('尝试匹配包含 **maps_geo** 的格式')
    const mapsGeoPattern = /🔧 \*\*maps_geo\*\*[\s\S]*?(\[.*?\])/gs
    formatted = content.replace(mapsGeoPattern, (match, result) => {
      console.log('匹配到maps_geo格式:', result)
      const formattedToolName = `🔧 **maps_geo**`
      let formattedResult = result
      
      // 处理地理编码结果
      formattedResult = formatGeoData(formattedResult)
      
      return `<div class="tool-result">${formattedToolName}\n\n${formattedResult}</div>\n\n`
    })
  }
  
  // 如果还是没有匹配到，尝试匹配更完整的格式（使用平衡括号匹配）
  if (formatted === content) {
    console.log('尝试匹配更完整的格式')
    const fullPattern = /🔧 \*\*maps_geo\*\*[\s\S]*?(\[.*?\])[\s\S]*?(?=🔧|$)/gs
    formatted = content.replace(fullPattern, (match, result) => {
      console.log('匹配到完整格式:', result)
      const formattedToolName = `🔧 **maps_geo**`
      let formattedResult = result
      
      // 处理地理编码结果
      formattedResult = formatGeoData(formattedResult)
      
      return `<div class="tool-result">${formattedToolName}\n\n${formattedResult}</div>\n\n`
    })
  }
  
  // 如果还是没有匹配到，尝试匹配平衡括号的JSON数组
  if (formatted === content) {
    console.log('尝试匹配平衡括号的JSON数组')
    const balancedPattern = /🔧 \*\*maps_geo\*\*[\s\S]*?(\[.*?\])/gs
    formatted = content.replace(balancedPattern, (match, result) => {
      console.log('匹配到平衡括号格式:', result)
      const formattedToolName = `🔧 **maps_geo**`
      let formattedResult = result
      
      // 处理地理编码结果
      formattedResult = formatGeoData(formattedResult)
      
      return `<div class="tool-result">${formattedToolName}\n\n${formattedResult}</div>\n\n`
    })
  }
  
  // 如果还是没有匹配到，尝试手动解析JSON数组
  if (formatted === content) {
    console.log('尝试手动解析JSON数组')
    const mapsGeoMatch = content.match(/🔧 \*\*maps_geo\*\*([\s\S]*)/)
    if (mapsGeoMatch) {
      console.log('找到maps_geo内容:', mapsGeoMatch[1])
      const contentAfterTool = mapsGeoMatch[1].trim()
      
      // 查找JSON数组的开始和结束
      const jsonStart = contentAfterTool.indexOf('[')
      if (jsonStart !== -1) {
        let bracketCount = 0
        let jsonEnd = jsonStart
        
        for (let i = jsonStart; i < contentAfterTool.length; i++) {
          if (contentAfterTool[i] === '[') {
            bracketCount++
          } else if (contentAfterTool[i] === ']') {
            bracketCount--
            if (bracketCount === 0) {
              jsonEnd = i
              break
            }
          }
        }
        
        if (bracketCount === 0) {
          const jsonArray = contentAfterTool.substring(jsonStart, jsonEnd + 1)
          console.log('提取到JSON数组:', jsonArray)
          
          const formattedToolName = `🔧 **maps_geo**`
          let formattedResult = jsonArray
          
          // 处理地理编码结果
          formattedResult = formatGeoData(formattedResult)
          
          formatted = `<div class="tool-result">${formattedToolName}\n\n${formattedResult}</div>\n\n`
        }
      }
    }
  }
  
  // 如果没有匹配到，尝试匹配原始格式
  if (formatted === content) {
    console.log('尝试匹配原始格式')
    const originalPattern = /当前工具：([^,]+),调用结果：(.+?)(?=当前工具：|$)/gs
    formatted = content.replace(originalPattern, (match, toolName, result) => {
      console.log('匹配到原始格式工具调用:', toolName, result)
      const formattedToolName = `🔧 **${toolName}**`
      let formattedResult = result.trim()
      
      // 特殊处理POI数据
      if (toolName.includes('around_search') || toolName.includes('text_search')) {
        formattedResult = formatPoiData(formattedResult)
      } else if (toolName.includes('maps_geo')) {
        // 处理地理编码结果
        formattedResult = formatGeoData(formattedResult)
      } else {
        // 其他工具结果进行JSON格式化
        formattedResult = formatJsonData(formattedResult)
      }
      
      return `<div class="tool-result">${formattedToolName}\n\n${formattedResult}</div>\n\n`
    })
  }
  
  console.log('工具调用结果处理完成:', formatted)
  return formatted
}

/**
 * 格式化JSON数据
 */
function formatJsonData(content: string): string {
  // 尝试解析和格式化JSON
  try {
    // 查找JSON字符串
    const jsonPattern = /```json\s*([\s\S]*?)\s*```/g
    let formatted = content.replace(jsonPattern, (match, jsonStr) => {
      try {
        const parsed = JSON.parse(jsonStr)
        return `\`\`\`json\n${JSON.stringify(parsed, null, 2)}\n\`\`\``
      } catch {
        return match
      }
    })

    // 查找独立的JSON对象
    const jsonObjectPattern = /\{[\s\S]*?\}/g
    formatted = formatted.replace(jsonObjectPattern, (match) => {
      try {
        const parsed = JSON.parse(match)
        return `\`\`\`json\n${JSON.stringify(parsed, null, 2)}\n\`\`\``
      } catch {
        return match
      }
    })

    return formatted
  } catch {
    return content
  }
}

/**
 * 格式化Markdown
 */
function formatMarkdown(content: string): string {
  try {
    return marked.parse(content)
  } catch {
    return content
  }
}

/**
 * 高亮显示工具名称
 */
export function highlightToolNames(content: string): string {
  const toolPattern = /(maps_\w+|search_\w+|weather_\w+)/g
  return content.replace(toolPattern, '🔧 **$1**')
}

/**
 * 格式化POI数据（兴趣点）
 */
function formatPoiData(content: string): string {
  try {
    // 先尝试直接解析整个内容
    let data = null
    try {
      data = JSON.parse(content)
    } catch {
      // 如果直接解析失败，尝试查找JSON代码块
      const jsonMatch = content.match(/```json\s*([\s\S]*?)\s*```/)
      if (jsonMatch) {
        data = JSON.parse(jsonMatch[1])
      }
    }
    
    if (data) {
      // 处理嵌套的text字段
      if (data.type === 'text' && data.text) {
        try {
          const innerData = JSON.parse(data.text)
          if (innerData.pois && Array.isArray(innerData.pois)) {
            return formatPoiList(innerData.pois)
          }
        } catch {
          // 如果内层解析失败，继续处理外层
        }
      }
      
      // 直接处理pois数组
      if (data.pois && Array.isArray(data.pois)) {
        return formatPoiList(data.pois)
      }
    }
    
    // 尝试从原始文本中提取POI数据
    const poiPattern = /"pois":\s*\[([\s\S]*?)\]/g
    const match = poiPattern.exec(content)
    if (match) {
      try {
        const poisJson = `{"pois": [${match[1]}]}`
        const data = JSON.parse(poisJson)
        if (data.pois && Array.isArray(data.pois)) {
          return formatPoiList(data.pois)
        }
      } catch (error) {
        console.error('POI提取错误:', error)
      }
    }
    
    return content
  } catch (error) {
    console.error('POI格式化错误:', error)
    return content
  }
}

/**
 * 格式化POI列表
 */
function formatPoiList(pois: any[]): string {
  let poiHtml = '<div class="poi-list">\n'
  
  pois.forEach((poi: any, index: number) => {
    poiHtml += `<div class="poi-item">
      <div class="poi-header">
        <span class="poi-index">${index + 1}</span>
        <h4 class="poi-name">${poi.name || '未知名称'}</h4>
      </div>
      <div class="poi-details">
        <p class="poi-address">📍 ${poi.address || '地址未知'}</p>
        ${poi.typecode ? `<span class="poi-type">${getTypeName(poi.typecode)}</span>` : ''}
        ${poi.photos && poi.photos.url ? `<div class="poi-photo"><img src="${poi.photos.url}" alt="${poi.name}" loading="lazy" /></div>` : ''}
      </div>
    </div>\n`
  })
  
  poiHtml += '</div>'
  return poiHtml
}

/**
 * 格式化地理编码数据
 */
function formatGeoData(content: string): string {
  console.log('开始处理地理编码数据:', content)
  
  try {
    // 先尝试直接解析整个内容
    let data = null
    try {
      data = JSON.parse(content)
      console.log('直接解析成功:', data)
    } catch (e) {
      console.log('直接解析失败:', e)
      return content
    }
    
    if (data) {
      // 处理数组格式的数据
      if (Array.isArray(data)) {
        console.log('处理数组格式数据')
        for (const item of data) {
          if (item.type === 'text' && item.text) {
            console.log('处理嵌套text字段:', item.text)
            try {
              const innerData = JSON.parse(item.text)
              console.log('内层数据解析成功:', innerData)
              if (innerData.return && Array.isArray(innerData.return)) {
                console.log('找到return数组，开始格式化')
                return formatGeoList(innerData.return)
              }
            } catch (e) {
              console.log('内层解析失败:', e)
            }
          }
        }
      }
      
      // 处理嵌套的text字段
      if (data.type === 'text' && data.text) {
        console.log('处理嵌套text字段:', data.text)
        try {
          const innerData = JSON.parse(data.text)
          console.log('内层数据解析成功:', innerData)
          if (innerData.return && Array.isArray(innerData.return)) {
            console.log('找到return数组，开始格式化')
            return formatGeoList(innerData.return)
          }
        } catch (e) {
          console.log('内层解析失败:', e)
        }
      }
      
      // 直接处理return数组
      if (data.return && Array.isArray(data.return)) {
        console.log('直接处理return数组')
        return formatGeoList(data.return)
      }
    }
    
    console.log('地理编码数据格式化失败，返回原内容')
    return content
  } catch (error) {
    console.error('地理编码格式化错误:', error)
    return content
  }
}

/**
 * 格式化地理编码列表
 */
function formatGeoList(geoResults: any[]): string {
  let geoHtml = '<div class="geo-list">\n'
  
  geoResults.forEach((geo: any, index: number) => {
    geoHtml += `<div class="geo-item">
      <div class="geo-header">
        <span class="geo-index">${index + 1}</span>
        <h4 class="geo-name">${geo.district || geo.city || '未知位置'}</h4>
      </div>
      <div class="geo-details">
        <p class="geo-address">📍 ${geo.country} ${geo.province} ${geo.city} ${geo.district}</p>
        <div class="geo-info">
          <span class="geo-level">${geo.level || '未知级别'}</span>
          ${geo.citycode ? `<span class="geo-code">区号: ${geo.citycode}</span>` : ''}
          ${geo.adcode ? `<span class="geo-adcode">编码: ${geo.adcode}</span>` : ''}
        </div>
        ${geo.location ? `<div class="geo-coords">🗺️ 坐标: ${geo.location}</div>` : ''}
      </div>
    </div>\n`
  })
  
  geoHtml += '</div>'
  return geoHtml
}

/**
 * 获取POI类型名称
 */
function getTypeName(typecode: string): string {
  const typeMap: Record<string, string> = {
    '050000': '餐厅',
    '050100': '美食广场',
    '050200': '快餐',
    '050300': '咖啡厅',
    '050400': '茶楼',
    '050500': '酒吧',
    '050600': 'KTV',
    '050700': '夜总会',
    '050800': '网吧',
    '050900': '游戏厅',
    '051000': '电影院',
    '051100': '剧院',
    '051200': '博物馆',
    '051300': '图书馆',
    '051400': '展览馆',
    '051500': '体育馆',
    '051600': '游泳馆',
    '051700': '健身房',
    '051800': '美容院',
    '051900': '洗浴中心',
    '052000': '按摩店',
    '052100': '足浴店',
    '052200': '理发店',
    '052300': '洗衣店',
    '052400': '维修店',
    '052500': '药店',
    '052600': '医院',
    '052700': '诊所',
    '052800': '银行',
    '052900': 'ATM',
    '053000': '邮局',
    '053100': '电信营业厅',
    '053200': '移动营业厅',
    '053300': '联通营业厅',
    '053400': '加油站',
    '053500': '停车场',
    '053600': '公交站',
    '053700': '地铁站',
    '053800': '火车站',
    '053900': '机场',
    '054000': '港口',
    '054100': '汽车站',
    '054200': '出租车停靠点',
    '054300': '自行车租赁点',
    '054400': '共享单车点',
    '054500': '充电桩',
    '054600': '垃圾站',
    '054700': '公厕',
    '054800': '公园',
    '054900': '广场',
    '055000': '景区',
    '055100': '酒店',
    '055200': '宾馆',
    '055300': '民宿',
    '055400': '青年旅社',
    '055500': '度假村',
    '055600': '温泉',
    '055700': '滑雪场',
    '055800': '高尔夫球场',
    '055900': '马场',
    '056000': '射击场',
    '056100': '攀岩场',
    '056200': '蹦极场',
    '056300': '漂流',
    '056400': '潜水',
    '056500': '冲浪',
    '056600': '帆船',
    '056700': '游艇',
    '056800': '热气球',
    '056900': '滑翔伞',
    '057000': '跳伞',
    '057100': '滑翔机',
    '057200': '直升机',
    '057300': '飞机',
    '057400': '火车',
    '057500': '汽车',
    '057600': '摩托车',
    '057700': '自行车',
    '057800': '电动车',
    '057900': '滑板',
    '058000': '轮滑',
    '058100': '滑冰',
    '058200': '滑雪',
    '058300': '滑水',
    '058400': '滑沙',
    '058500': '滑草',
    '058600': '滑翔',
    '058700': '滑索',
    '058800': '滑梯',
    '058900': '滑道',
    '059000': '滑轨',
    '059100': '滑车',
    '059200': '滑船',
    '059300': '滑艇',
    '059400': '滑板车',
    '059500': '滑板鞋',
    '059600': '滑板轮',
    '059700': '滑板桥',
    '059800': '滑板砂纸',
    '059900': '滑板轴承',
    '060000': '滑板螺丝',
    '060100': '滑板垫片',
    '060200': '滑板垫圈',
    '060300': '滑板垫块',
    '060400': '滑板垫条',
    '060500': '滑板垫板',
    '060600': '滑板垫片',
    '060700': '滑板垫圈',
    '060800': '滑板垫块',
    '060900': '滑板垫条',
    '061000': '滑板垫板'
  }
  
  return typeMap[typecode] || `类型码: ${typecode}`
}

/**
 * 美化步骤信息
 */
export function formatStepInfo(content: string): string {
  // 处理 "Step X: " 前缀
  const stepPattern = /^Step \d+:\s*/gm
  return content.replace(stepPattern, '')
}
