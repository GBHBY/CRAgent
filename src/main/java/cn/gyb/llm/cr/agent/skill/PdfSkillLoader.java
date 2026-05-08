package cn.gyb.llm.cr.agent.skill;

import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;

@Slf4j
@Component
public class PdfSkillLoader implements SkillLoader {

    @Override
    public boolean supports(SkillType type) {
        return SkillType.PDF == type;
    }

    @Override
    public String loadContent(String sourcePath) {
        try (PDDocument document = PDDocument.load(new File(sourcePath))) {
            PDFTextStripper stripper = new PDFTextStripper();
            String text = stripper.getText(document);
            log.info("加载 PDF 内容: {}, 长度={}", sourcePath, text.length());
            return text;
        } catch (IOException e) {
            log.error("加载 PDF 失败: {}: {}", sourcePath, e.getMessage(), e);
            throw new RuntimeException("加载 PDF 失败: " + sourcePath, e);
        }
    }
}
