package com.fileservice.codesync.service;

import com.fileservice.codesync.entity.CodeFile;
import com.fileservice.codesync.repository.FileRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@Transactional
public class FileServiceImpl {

    private static final Logger log = LoggerFactory.getLogger(FileServiceImpl.class);

    private final FileRepository fileRepository;

    public FileServiceImpl(FileRepository fileRepository) {
        this.fileRepository = fileRepository;
    }

    public CodeFile createFile(Long projectId, String name, String path,
                               String language, String content, Long createdById) {
        if (fileRepository.findByProjectIdAndPath(projectId, path).isPresent()) {
            throw new IllegalArgumentException("File already exists at path: " + path);
        }
        CodeFile f = CodeFile.builder()
                .projectId(projectId).name(name).path(path)
                .language(language).content(content)
                .size(content != null ? (long) content.length() : 0L)
                .createdById(createdById).lastEditedBy(createdById)
                .isFolder(false)
                .build();
        CodeFile saved = fileRepository.save(f);
        log.info("Created file '{}' in project {}", path, projectId);
        return saved;
    }

    public CodeFile createFolder(Long projectId, String name, String path, Long createdById) {
        CodeFile f = CodeFile.builder()
                .projectId(projectId).name(name).path(path)
                .createdById(createdById).isFolder(true)
                .build();
        CodeFile saved = fileRepository.save(f);
        log.info("Created folder '{}' in project {}", path, projectId);
        return saved;
    }

    @Transactional(readOnly = true)
    public Optional<CodeFile> getFileById(Long fileId) {
        return fileRepository.findById(fileId);
    }

    @Transactional(readOnly = true)
    public List<CodeFile> getFilesByProject(Long projectId) {
        return fileRepository.findByProjectIdAndIsDeletedFalse(projectId);
    }

    @Transactional(readOnly = true)
    public String getFileContent(Long fileId) {
        return fileRepository.findById(fileId)
                .filter(f -> !f.isDeleted())
                .map(CodeFile::getContent)
                .orElseThrow(() -> new IllegalArgumentException("File not found: " + fileId));
    }

    @Transactional(readOnly = true)
    public List<CodeFile> getFileTree(Long projectId) {
        return fileRepository.findByProjectIdAndIsDeletedFalse(projectId);
    }

    public CodeFile updateFileContent(Long fileId, String newContent, Long editorId) {
        CodeFile f = findActive(fileId);
        f.setContent(newContent);
        f.setSize(newContent != null ? (long) newContent.length() : 0L);
        f.setLastEditedBy(editorId);
        return fileRepository.save(f);
    }

    public CodeFile renameFile(Long fileId, String newName) {
        CodeFile f = findActive(fileId);
        String oldPath = f.getPath();
        String newPath = oldPath.contains("/")
                ? oldPath.substring(0, oldPath.lastIndexOf('/') + 1) + newName
                : newName;
        f.setName(newName);
        f.setPath(newPath);
        return fileRepository.save(f);
    }

    public CodeFile moveFile(Long fileId, String newPath) {
        CodeFile f = findActive(fileId);
        String newName = newPath.contains("/")
                ? newPath.substring(newPath.lastIndexOf('/') + 1)
                : newPath;
        f.setPath(newPath);
        f.setName(newName);
        return fileRepository.save(f);
    }

    public void deleteFile(Long fileId) {
        CodeFile f = findActive(fileId);
        f.setDeleted(true);
        fileRepository.save(f);
        log.info("Soft-deleted file {}", fileId);
    }

    public CodeFile restoreFile(Long fileId) {
        CodeFile f = fileRepository.findById(fileId)
                .orElseThrow(() -> new IllegalArgumentException("File not found: " + fileId));
        f.setDeleted(false);
        return fileRepository.save(f);
    }

    @Transactional(readOnly = true)
    public List<CodeFile> searchInProject(Long projectId, String query) {
        return fileRepository.searchInProject(projectId, query);
    }

    private CodeFile findActive(Long fileId) {
        return fileRepository.findById(fileId)
                .filter(f -> !f.isDeleted())
                .orElseThrow(() -> new IllegalArgumentException("Active file not found: " + fileId));
    }
}
