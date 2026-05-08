package cn.gyb.llm.cr.agent.entity.record;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * GitLab 差异记录
 * <p>
 * 表示 GitLab 合并请求中单个文件的差异信息，
 * 包含新旧文件路径、文件变更类型标记和差异内容。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GitLabDiff {

    /** 变更前的文件路径 */
    private String oldPath;

    /** 变更后的文件路径 */
    private String newPath;

    /** 是否为新增文件 */
    private boolean newFile;

    /** 是否为删除文件 */
    private boolean deletedFile;

    /** 是否为重命名文件 */
    private boolean renamedFile;

    /** 差异内容（unified diff 格式） */
    private String diff;
}
