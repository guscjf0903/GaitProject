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
    
    // Spring AI (Gemini, OpenAI)
    // 참고: Spring AI는 아직 정식 릴리즈가 아니므로 Milestone 저장소 필요
    // 최신 버전 확인: https://repo.spring.io/ui/native/milestone/org/springframework/ai/
    // TODO: Spring AI 의존성 추가 시 주석 해제
    // implementation("org.springframework.ai:spring-ai-openai-spring-boot-starter:1.0.0-M4")
    // implementation("org.springframework.ai:spring-ai-vertex-ai-gemini-spring-boot-starter:1.0.0-M4")
    
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

