package cn.gyb.llm.cr.agent.skill;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;

@Slf4j
@Component
public class TextSkillLoader implements SkillLoader {

    @Override
    public boolean supports(SkillType type) {
        return SkillType.TEXT == type;
    }

    @Override
    public String loadContent(String sourcePath) {
        try {
            String content = Files.readString(Path.of(sourcePath));
            log.info("加载文本内容: {}, 长度={}", sourcePath, content.length());
            return content;
        } catch (Exception e) {
            log.error("读取文本文件失败: {}: {}", sourcePath, e.getMessage(), e);
            throw new RuntimeException("读取文本文件失败: " + sourcePath, e);
        }
    }
}
