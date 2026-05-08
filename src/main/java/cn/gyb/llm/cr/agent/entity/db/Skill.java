package cn.gyb.llm.cr.agent.entity.db;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 技能配置实体
 * <p>
 * 对应数据库表 cr_skill，用于存储代码审查规则技能的定义信息。
 * 每个 Skill 代表一组审查规则，可以是 PDF 文档、纯文本或自定义类型。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("cr_skill")
public class Skill {

    /** 主键ID */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** Skill唯一标识编码 */
    private String skillCode;

    /** 显示名称 */
    private String skillName;

    /** 类型 (PDF/TEXT/CUSTOM) */
    private String skillType;

    /** 覆盖范围描述 */
    private String description;

    /** 文件路径或URL */
    private String sourcePath;

    /** 解析后的文本内容 */
    private String content;

    /** 是否启用 (1=启用, 0=禁用) */
    private Integer enabled;

    /** 内容版本号 */
    private Integer version;

    /** 最后刷新时间 */
    private LocalDateTime lastRefreshedAt;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 更新时间 */
    private LocalDateTime updatedAt;
}
