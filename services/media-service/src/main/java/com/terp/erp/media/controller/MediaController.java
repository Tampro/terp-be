package com.terp.erp.media.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import com.terp.erp.common.ApiMessageDto;
import com.terp.erp.common.ResponseListDto;
import com.terp.erp.media.constant.EMediaKind;
import com.terp.erp.media.constant.EMediaSort;
import com.terp.erp.media.service.MediaDtos.FileUpdateForm;
import com.terp.erp.media.service.MediaDtos.FolderDto;
import com.terp.erp.media.service.MediaDtos.FolderForm;
import com.terp.erp.media.service.MediaDtos.MediaFileDto;
import com.terp.erp.media.service.MediaDtos.StatsDto;
import com.terp.erp.media.service.MediaService;

import java.util.List;

/**
 * Media library (docs/analysis/06 §3.2.2). A super admin sees every company's files (or one, with companyId) and
 * uploads platform files when no companyId is given; a company user only ever sees and writes its own company's.
 */
@Tag(name = "Media library")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/media")
@SecurityRequirement(name = "bearerAuth")
public class MediaController {
    private static final String COMPANY = "Super admin only. Lists: omitted = all companies. Writes: omitted = platform files.";

    private final MediaService media;

    @Operation(summary = "Folders with their file count")
    @PreAuthorize("@PermissionChecker.has('MEDIA_VIEW')")
    @GetMapping("/folders")
    public ApiMessageDto<List<FolderDto>> folders(@Parameter(description = COMPANY) @RequestParam(required = false) Long companyId) {
        return ApiMessageDto.ok(media.folders(companyId));
    }

    @Operation(summary = "Create a folder")
    @ApiResponses(@ApiResponse(responseCode = "400", description = "Name taken (MEDIA_003)"))
    @PreAuthorize("@PermissionChecker.has('MEDIA_UPLOAD')")
    @PostMapping("/folders")
    public ApiMessageDto<FolderDto> createFolder(@Parameter(description = COMPANY) @RequestParam(required = false) Long companyId,
                                                 @Valid @RequestBody FolderForm form) {
        return ApiMessageDto.ok(media.createFolder(companyId, form.name()));
    }

    @Operation(summary = "Rename a folder")
    @ApiResponses({
            @ApiResponse(responseCode = "400", description = "Name taken (MEDIA_003)"),
            @ApiResponse(responseCode = "404", description = "No such folder in your company (MEDIA_002)")})
    @PreAuthorize("@PermissionChecker.has('MEDIA_UPLOAD')")
    @PutMapping("/folders/{id}")
    public ApiMessageDto<FolderDto> renameFolder(@PathVariable Long id, @Valid @RequestBody FolderForm form) {
        return ApiMessageDto.ok(media.renameFolder(id, form.name()));
    }

    @Operation(summary = "Delete a folder", description = "Its files are kept and moved out of the folder.")
    @ApiResponses(@ApiResponse(responseCode = "404", description = "No such folder in your company (MEDIA_002)"))
    @PreAuthorize("@PermissionChecker.has('MEDIA_DELETE')")
    @DeleteMapping("/folders/{id}")
    public ApiMessageDto<Void> deleteFolder(@PathVariable Long id) {
        media.deleteFolder(id);
        return ApiMessageDto.ok(null);
    }

    @Operation(summary = "Search files",
            description = "keyword matches display or original name; kind IMAGE | DOCUMENT; sort NAME_ASC, NAME_DESC, "
                    + "DATE_DESC (default), DATE_ASC, SIZE_DESC, SIZE_ASC, TYPE. Each item carries time-limited "
                    + "viewUrl/downloadUrl for previews.")
    @PreAuthorize("@PermissionChecker.has('MEDIA_VIEW')")
    @GetMapping("/files")
    public ApiMessageDto<ResponseListDto<MediaFileDto>> files(
            @Parameter(description = COMPANY) @RequestParam(required = false) Long companyId,
            @RequestParam(required = false) Long folderId,
            @RequestParam(required = false) EMediaKind kind,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) EMediaSort sort,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "24") int size) {
        return ApiMessageDto.ok(ResponseListDto.of(media.search(companyId, folderId, kind, keyword, sort, page, size)));
    }

    @Operation(summary = "Summary cards: files, storage used (bytes), images",
            description = "storageLimitBytes = the company plan's storage limit (null = unlimited, or the super admin "
                    + "viewing every company / the platform). Read from IAM.")
    @ApiResponses(@ApiResponse(responseCode = "503", description = "IAM unreachable for the limit (MEDIA_011)"))
    @PreAuthorize("@PermissionChecker.has('MEDIA_VIEW')")
    @GetMapping("/stats")
    public ApiMessageDto<StatsDto> stats(@Parameter(description = COMPANY) @RequestParam(required = false) Long companyId) {
        return ApiMessageDto.ok(media.stats(companyId));
    }

    @Operation(summary = "Media details", description = "Fresh viewUrl/downloadUrl (valid 10 minutes).")
    @ApiResponses(@ApiResponse(responseCode = "404", description = "No such file in your company (MEDIA_001)"))
    @PreAuthorize("@PermissionChecker.has('MEDIA_VIEW')")
    @GetMapping("/files/{id}")
    public ApiMessageDto<MediaFileDto> file(@PathVariable Long id) {
        return ApiMessageDto.ok(media.get(id));
    }

    @Operation(summary = "Upload files (multipart field `files`, one or more)",
            description = "Allowed: png, jpg, jpeg, gif, webp, pdf, doc, docx, xls, xlsx, pptx, txt, csv; each up to "
                    + "the size limit. The type is checked from the file content; nothing is kept if one file is refused.")
    @ApiResponses({
            @ApiResponse(responseCode = "400", description = "No file (MEDIA_008), type not allowed (MEDIA_004), too "
                    + "large (MEDIA_005), content does not match type (MEDIA_006), storage error (MEDIA_009), "
                    + "the company plan's storage limit would be passed (MEDIA_010)"),
            @ApiResponse(responseCode = "404", description = "Folder not in your company (MEDIA_002)"),
            @ApiResponse(responseCode = "503", description = "IAM unreachable for the storage limit (MEDIA_011)")})
    @PreAuthorize("@PermissionChecker.has('MEDIA_UPLOAD')")
    @PostMapping(value = "/files", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiMessageDto<List<MediaFileDto>> upload(
            @Parameter(description = COMPANY) @RequestParam(required = false) Long companyId,
            @RequestParam(required = false) Long folderId,
            @RequestPart("files") List<MultipartFile> files) {
        return ApiMessageDto.ok(media.upload(companyId, folderId, files));
    }

    @Operation(summary = "Rename / move a file (full replacement: omitted folderId = no folder)")
    @ApiResponses(@ApiResponse(responseCode = "404", description = "No such file (MEDIA_001) or folder (MEDIA_002) in your company"))
    @PreAuthorize("@PermissionChecker.has('MEDIA_UPLOAD')")
    @PutMapping("/files/{id}")
    public ApiMessageDto<MediaFileDto> update(@PathVariable Long id, @Valid @RequestBody FileUpdateForm form) {
        return ApiMessageDto.ok(media.update(id, form.displayName(), form.folderId()));
    }

    @Operation(summary = "Delete a file permanently")
    @ApiResponses(@ApiResponse(responseCode = "404", description = "No such file in your company (MEDIA_001)"))
    @PreAuthorize("@PermissionChecker.has('MEDIA_DELETE')")
    @DeleteMapping("/files/{id}")
    public ApiMessageDto<Void> delete(@PathVariable Long id) {
        media.delete(id);
        return ApiMessageDto.ok(null);
    }
}
