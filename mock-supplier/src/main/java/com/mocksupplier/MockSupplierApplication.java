package com.mocksupplier;

import java.util.Map;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 공급사 A, B 를 흉내내는 Mock 서버. 채점 대상이 아니므로 단순하게 둔다.
 * application.yml 을 두지 않는다. app 모듈 테스트 클래스패스에 올라갈 때 app 설정을 가리지 않게 하기 위함.
 * 포트 기본값 9090 은 --server.port 로 바꿀 수 있다.
 */
@SpringBootApplication
public class MockSupplierApplication {

    public static void main(String[] args) {
        SpringApplication application = new SpringApplication(MockSupplierApplication.class);
        application.setDefaultProperties(Map.of("server.port", "9090"));
        application.run(args);
    }
}
