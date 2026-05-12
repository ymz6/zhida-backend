package org.ymz.app.model.dto.app;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.ymz.app.model.enums.app.FileNodeType;

import java.util.List;

/**
 * 应用工作区文件节点
 *
 * @author ymz
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class FileNode {

    private String title;

    private String path;

    private FileNodeType type;

    private List<FileNode> children;

    private String content;
}
