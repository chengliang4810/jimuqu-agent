package com.jimuqu.agent.llm;

import com.jimuqu.agent.config.AppConfig;
import org.junit.jupiter.api.Test;
import org.noear.solon.ai.skills.pdf.PdfSkill;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

public class SolonAiPdfSkillTest {
    @Test
    void shouldCreateAndParsePdfViaOfficialPdfSkill() throws Exception {
        Path runtimeHome = Files.createTempDirectory("jimuqu-pdf-skill");
        AppConfig config = new AppConfig();
        config.getRuntime().setCacheDir(runtimeHome.resolve("cache").toAbsolutePath().toString());

        SolonAiLlmGateway gateway = new SolonAiLlmGateway(config);
        PdfSkill pdfSkill = gateway.pdfSkill();

        String result = pdfSkill.create("report.pdf", "# Solon PDF Test\n\nHello PDF", "markdown");
        String parsed = pdfSkill.parse("report.pdf");

        assertThat(result).contains("PDF");
        assertThat(Files.exists(runtimeHome.resolve("cache").resolve("pdf").resolve("report.pdf"))).isTrue();
        assertThat(parsed).containsIgnoringCase("Solon");
        assertThat(parsed).containsIgnoringCase("Hello");
    }
}
