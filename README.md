pluginManagement {
	repositories {
		maven {
			name = 'Fabric'
			url = 'https://maven.aliyun.com/repository/public'
		}
		maven {
			name = 'MavenCentral'
			url = 'https://maven.aliyun.com/repository/central'
		}
		maven {
			name = 'GradlePlugins'
			url = 'https://maven.aliyun.com/repository/gradle-plugin'
		}
		// 保留原始仓库作为备用
		maven {
			name = 'FabricBackup'
			url = 'https://maven.fabricmc.net/'
		}
		mavenCentral()
		gradlePluginPortal()
	}
}

// Should match your modid
rootProject.name = 'template'
# Template

## Setup

For setup instructions, please see the [Fabric Documentation page](https://docs.fabricmc.net/develop/getting-started/creating-a-project#setting-up) related to the IDE that you are using.

## License

This template is available under the CC0 license. Feel free to learn from it and incorporate it in your own projects.
