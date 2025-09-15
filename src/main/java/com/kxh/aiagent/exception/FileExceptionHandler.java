package com.kxh.aiagent.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.nio.file.FileSystemException;

@RestControllerAdvice
public class FileExceptionHandler {

    @ExceptionHandler(FileSystemException.class)
    public ResponseEntity<String> handleFileSystemException(FileSystemException e) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body("文件系统错误: " + e.getMessage());
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<String> handleMaxSizeException(MaxUploadSizeExceededException e) {
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
                .body("文件大小超过限制");
    }
}