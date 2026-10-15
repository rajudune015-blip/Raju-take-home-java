package com.qa.assessment.ui;

import com.microsoft.playwright.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

class SkipLogicTest {
    private Playwright playwright;
    private Browser browser;
    private Page page;

    @BeforeEach void setUp() {
        playwright = Playwright.create();
        browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(true));
        page = browser.newPage();
        Path html = Path.of("src/test/resources/fixtures/dynamic-survey.html").toAbsolutePath();
        page.navigate(html.toUri().toString());
    }

    @AfterEach void tearDown() {
        if (browser != null) browser.close();
        if (playwright != null) playwright.close();
    }

    @Test void followUpAppearsWhenHobbyAnswerIsYes() {
        Locator followUp = page.getByLabel("What is your hobby? (If Yes)");
        assertThat(followUp).isHidden();
        page.getByLabel("Yes", new Page.GetByLabelOptions().setExact(true)).check();
        assertThat(followUp).isVisible();
        assertThat(followUp).hasAttribute("required", "");
    }

    @Test void followUpHidesAndClearsWhenAnswerChangesToNo() {
        Locator followUp = page.getByLabel("What is your hobby? (If Yes)");
        page.getByLabel("Yes", new Page.GetByLabelOptions().setExact(true)).check();
        followUp.fill("Drawing");
        page.getByLabel("No", new Page.GetByLabelOptions().setExact(true)).check();
        assertThat(followUp).isHidden();
        assertThat(followUp).hasValue("");
        assertThat(followUp).not().hasAttribute("required", "");
    }
}
