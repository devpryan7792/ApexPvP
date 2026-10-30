plugins {
    id("com.gradleup.shadow")
}

dependencies {
    // Paper 26.3 / modern Paper API
    compileOnly("io.papermc.paper:paper-api:26.3.build.231-stable")

    // Database Pooling & Drivers (shaded)
    implementation("com.zaxxer:HikariCP:5.1.0")
    implementation("org.xerial:sqlite-jdbc:3.45.2.0")
    implementation("org.postgresql:postgresql:42.7.3")
    implementation("org.mariadb.jdbc:mariadb-java-client:3.3.3")
    implementation("com.mysql:mysql-connector-j:8.3.0")

    // Annotations
    compileOnly("org.jetbrains:annotations:24.1.0")

    // Soft & Hard API Hooks
    compileOnly("com.github.MilkBowl:VaultAPI:1.7.1")
    compileOnly("net.citizensnpcs:citizens-main:2.0.35-SNAPSHOT") {
        exclude(group = "*", module = "*")
    }
    compileOnly("me.clip:placeholderapi:2.11.5")
    compileOnly("com.fastasyncworldedit:FastAsyncWorldEdit-Core:2.9.2") {
        isTransitive = false
    }
    compileOnly("com.fastasyncworldedit:FastAsyncWorldEdit-Bukkit:2.9.2") {
        isTransitive = false
    }

    // Testing
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
    testImplementation("org.mockito:mockito-core:5.11.0")
    testImplementation("io.papermc.paper:paper-api:26.3.build.231-stable")
    testImplementation("com.github.MilkBowl:VaultAPI:1.7.1")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
    systemProperty("net.bytebuddy.experimental", "true")
    jvmArgs("-XX:+EnableDynamicAgentLoading")
}

tasks.shadowJar {
    archiveClassifier.set("")
    relocate("com.zaxxer.hikari", "com.apexpvp.libs.hikari")
    relocate("org.postgresql", "com.apexpvp.libs.postgresql")
    relocate("org.mariadb.jdbc", "com.apexpvp.libs.mariadb")
    relocate("com.mysql", "com.apexpvp.libs.mysql")
}

tasks.build {
    dependsOn(tasks.shadowJar)
}

tasks.processResources {
    val props = mapOf("version" to project.version)
    inputs.properties(props)
    filteringCharset = "UTF-8"
    filesMatching("plugin.yml") {
        expand(props)
    }
}
