const { withProjectBuildGradle, withGradleProperties } = require('@expo/config-plugins');

function withComposeSuppression(config) {
  // 1. Add kotlin.suppressKotlinVersionCompatibilityCheck=true to gradle.properties
  config = withGradleProperties(config, (cfg) => {
    cfg.modResults.push({
      type: 'property',
      key: 'kotlin.suppressKotlinVersionCompatibilityCheck',
      value: 'true',
    });
    return cfg;
  });

  // 2. Add compiler flag to all projects in root build.gradle
  config = withProjectBuildGradle(config, (cfg) => {
    const buildGradle = cfg.modResults.contents;
    const suppressionBlock = `
allprojects {
    tasks.withType(org.jetbrains.kotlin.gradle.tasks.KotlinCompile).configureEach {
        kotlinOptions {
            freeCompilerArgs += [
                "-P",
                "plugin:androidx.compose.compiler.plugins.kotlin:suppressKotlinVersionCompatibilityCheck=true"
            ]
        }
    }
}
`;
    if (!buildGradle.includes('suppressKotlinVersionCompatibilityCheck')) {
      cfg.modResults.contents = buildGradle + suppressionBlock;
    }
    return cfg;
  });

  return config;
}

module.exports = withComposeSuppression;
