package com.powerrag.bdd;

import org.junit.platform.suite.api.ConfigurationParameter;
import org.junit.platform.suite.api.IncludeEngines;
import org.junit.platform.suite.api.SelectClasspathResource;
import org.junit.platform.suite.api.Suite;

import static io.cucumber.junit.platform.engine.Constants.*;

/**
 * JUnit Platform Suite runner for Cucumber BDD scenarios.
 * Reports are generated in:
 *   target/cucumber-reports/cucumber.html
 *   target/cucumber-reports/cucumber.json
 */
@Suite
@IncludeEngines("cucumber")
@SelectClasspathResource("features")
@ConfigurationParameter(key = PLUGIN_PROPERTY_NAME,
        value = "pretty," +
                "html:target/cucumber-reports/cucumber.html," +
                "json:target/cucumber-reports/cucumber.json," +
                "junit:target/cucumber-reports/cucumber-junit.xml")
@ConfigurationParameter(key = GLUE_PROPERTY_NAME,
        value = "com.powerrag.bdd,com.powerrag.bdd.steps")
@ConfigurationParameter(key = FILTER_TAGS_PROPERTY_NAME,
        value = "not @ignore")
public class CucumberRunner {
    // Suite entry point – no code required
}
