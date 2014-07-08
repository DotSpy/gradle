/*
 * Copyright 2011 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.gradle.api.internal.tasks.testing.junit.report;

import org.gradle.api.Action;
import org.gradle.api.GradleException;
import org.gradle.api.internal.tasks.testing.junit.result.TestClassResult;
import org.gradle.api.internal.tasks.testing.junit.result.TestFailure;
import org.gradle.api.internal.tasks.testing.junit.result.TestMethodResult;
import org.gradle.api.internal.tasks.testing.junit.result.TestResultsProvider;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.reporting.HtmlReportRenderer;
import org.gradle.util.Clock;

import java.io.File;
import java.util.List;

import static org.gradle.api.tasks.testing.TestResult.ResultType.SKIPPED;

public class DefaultTestReport implements TestReporter {
    private final HtmlReportRenderer htmlRenderer = new HtmlReportRenderer();
    private final static Logger LOG = Logging.getLogger(DefaultTestReport.class);

    public DefaultTestReport() {
        htmlRenderer.requireResource(getClass().getResource("/org/gradle/reporting/report.js"));
        htmlRenderer.requireResource(getClass().getResource("/org/gradle/reporting/base-style.css"));
        htmlRenderer.requireResource(getClass().getResource("/org/gradle/reporting/css3-pie-1.0beta3.htc"));
        htmlRenderer.requireResource(getClass().getResource("style.css"));
    }

    public void generateReport(TestResultsProvider resultsProvider, File reportDir) {
        LOG.info("Generating HTML test report...");

        Clock clock = new Clock();
        AllTestResults model = loadModelFromProvider(resultsProvider);
        generateFiles(model, resultsProvider, reportDir);
        LOG.info("Finished generating test html results ({}) into: {}", clock.getTime(), reportDir);
    }

    private AllTestResults loadModelFromProvider(TestResultsProvider resultsProvider) {
        final AllTestResults model = new AllTestResults();
        resultsProvider.visitClasses(new Action<TestClassResult>() {
            public void execute(TestClassResult classResult) {
                model.addTestClass(classResult.getId(), classResult.getClassName());
                List<TestMethodResult> collectedResults = classResult.getResults();
                for (TestMethodResult collectedResult : collectedResults) {
                    final TestResult testResult = model.addTest(classResult.getId(), classResult.getClassName(), collectedResult.getName(), collectedResult.getDuration());
                    if (collectedResult.getResultType() == SKIPPED) {
                        testResult.setIgnored();
                    } else {
                        List<TestFailure> failures = collectedResult.getFailures();
                        for (TestFailure failure : failures) {
                            testResult.addFailure(failure);
                        }
                    }
                }
            }
        });
        return model;
    }

    private void generateFiles(AllTestResults model, TestResultsProvider resultsProvider, File reportDir) {
        try {
            generatePage(model, new OverviewPageRenderer(), new File(reportDir, "index.html"));
            for (PackageTestResults packageResults : model.getPackages()) {
                generatePage(packageResults, new PackagePageRenderer(), new File(reportDir, packageResults.getBaseUrl()));
                for (ClassTestResults classResults : packageResults.getClasses()) {
                    generatePage(classResults, new ClassPageRenderer(classResults.getId(), resultsProvider), new File(reportDir, classResults.getBaseUrl()));
                }
            }
        } catch (Exception e) {
            throw new GradleException(String.format("Could not generate test report to '%s'.", reportDir), e);
        }
    }

    private <T extends CompositeTestResults> void generatePage(T model, PageRenderer<T> renderer, File outputFile) throws Exception {
        htmlRenderer.renderer(renderer).render(model, outputFile);
    }
}
