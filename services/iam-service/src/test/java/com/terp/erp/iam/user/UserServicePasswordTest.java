package com.terp.erp.iam.user;

import org.junit.jupiter.api.RepeatedTest;

import static org.assertj.core.api.Assertions.assertThat;

class UserServicePasswordTest {

    @RepeatedTest(200)
    void temporaryPasswordAlwaysMeetsRealmPolicy() {
        String p = UserService.generatePassword();
        assertThat(p).hasSize(16);
        assertThat(p).containsPattern("[A-Z]").containsPattern("[a-z]").containsPattern("[0-9]")
                .containsPattern("[!@#$%]");
    }
}
