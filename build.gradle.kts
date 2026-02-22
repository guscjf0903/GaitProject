import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    id("org.springframework.boot") version "3.2.0"
    id("io.spring.dependency-management") version "1.1.4"
    kotlin("jvm") version "1.9.20"
    kotlin("plugin.spring") version "1.9.20"
    kotlin("plugin.jpa") version "1.9.20"
    kotlin("plugin.allopen") version "1.9.20"
    kotlin("plugin.noarg") version "1.9.20"
    kotlin("kapt") version "1.9.20"
}

group = "com.gait"
version = "0.0.1-SNAPSHOT"

java {
    // Spring Boot 3.2+ 는 Java 17+ 지원.
    // 로컬/CI 환경에서 JDK 버전 불일치로 빌드가 깨지지 않도록 17로 고정합니다.
    sourceCompatibility = JavaVersion.VERSION_17
}

repositories {
    mavenCentral()
    // Spring AI Milestone 저장소
    maven {
        url = uri("https://repo.spring.io/milestone")
    }
}

dependencies {
    // Spring Boot Starters
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-security")

    // OpenAPI (Swagger UI) - Spring Boot 3.x + WebMVC
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.3.0")
    
    // Spring AI (OpenAI)
    // - BOM으로 버전 일관성 유지
    // - OpenAI ChatModel/StreamingChatModel 자동 구성(starter) 사용
    implementation(platform("org.springframework.ai:spring-ai-bom:1.0.3"))
    implementation("org.springframework.ai:spring-ai-starter-model-openai")
    
    // HTTP 클라이언트 (직접 API 호출용, Spring AI 대안)
    implementation("org.springframework.boot:spring-boot-starter-webflux") // WebClient 사용
    
    // JWT (minimal)
    implementation("io.jsonwebtoken:jjwt-api:0.12.5")
    runtimeOnly("io.jsonwebtoken:jjwt-impl:0.12.5")
    runtimeOnly("io.jsonwebtoken:jjwt-jackson:0.12.5")
    
    // Kotlin
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    
    // Database
    implementation("org.postgresql:postgresql")
    runtimeOnly("com.h2database:h2") // 테스트용
    
    // QueryDSL (Jakarta)
    implementation("com.querydsl:querydsl-jpa:5.0.0:jakarta")
    kapt("com.querydsl:querydsl-apt:5.0.0:jakarta")
    
    // Lombok
    compileOnly("org.projectlombok:lombok")
    annotationProcessor("org.projectlombok:lombok")
    kapt("org.projectlombok:lombok")
    
    // Test
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<KotlinCompile> {
    kotlinOptions {
        freeCompilerArgs += listOf("-Xjsr305=strict")
        jvmTarget = "17"
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
}

// QueryDSL 설정
allOpen {
    annotation("jakarta.persistence.Entity")
    annotation("jakarta.persistence.MappedSuperclass")
    annotation("jakarta.persistence.Embeddable")
}

noArg {
    annotation("jakarta.persistence.Entity")
    annotation("jakarta.persistence.MappedSuperclass")
    annotation("jakarta.persistence.Embeddable")
}

// Kapt 설정
kapt {
    correctErrorTypes = true
    arguments {
        arg("querydsl.entityAccessors", "true")
    }
}

