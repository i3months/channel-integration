dependencies {
    implementation("org.springframework.boot:spring-boot-starter-web")
}

// app 모듈의 종단 테스트가 이 모듈을 클래스패스에 올린다. 일반 jar 도 만들어야 한다.
tasks.named<Jar>("jar") {
    enabled = true
    archiveClassifier.set("")
}

tasks.named<org.springframework.boot.gradle.tasks.bundling.BootJar>("bootJar") {
    archiveFileName.set("mock-supplier-boot.jar")
}
