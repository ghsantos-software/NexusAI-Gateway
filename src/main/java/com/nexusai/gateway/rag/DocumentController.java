package com.nexusai.gateway.rag;

import com.nexusai.gateway.auth.UserService;
import com.nexusai.gateway.rag.dto.UploadResponse;
import com.nexusai.gateway.shared.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/v1/rag")
@RequiredArgsConstructor
@Tag(name = "RAG - Documents", description = "Upload and manage documents for semantic context enrichment")
@SecurityRequirement(name = "Bearer Authentication")
public class DocumentController {

    private final RagService ragService;
    private final UserService userService;

    @PostMapping(value = "/documents", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(
            summary = "Upload a document",
            description = "Accepts .txt or .pdf files. Splits into chunks, generates embeddings, and stores in pgvector."
    )
    public ResponseEntity<ApiResponse<UploadResponse>> upload(
            @RequestParam("file") MultipartFile file) {

        var user = userService.getCurrentUser();
        var result = ragService.processDocument(file, user.getTenantId());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(result, "Document processed and embedded successfully"));
    }

    @GetMapping("/documents")
    @Operation(summary = "List uploaded documents", description = "Returns distinct document names for the current tenant")
    public ApiResponse<List<String>> listDocuments() {
        var user = userService.getCurrentUser();
        return ApiResponse.ok(ragService.listDocuments(user.getTenantId()));
    }

    @DeleteMapping("/documents/{documentName}")
    @Operation(summary = "Delete a document", description = "Removes all chunks and embeddings for the given document name")
    public ApiResponse<Void> deleteDocument(@PathVariable String documentName) {
        var user = userService.getCurrentUser();
        ragService.deleteDocument(documentName, user.getTenantId());
        return ApiResponse.ok(null, "Document deleted");
    }
}
