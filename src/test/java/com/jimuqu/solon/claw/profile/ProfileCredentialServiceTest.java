package com.jimuqu.solon.claw.profile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Collections;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Profile .env 凭据管理服务测试。 */
public class ProfileCredentialServiceTest {
    /** 临时 Profile 工作区。 */
    @TempDir Path home;

    /** 验证特殊字符安全往返、覆盖和删除。 */
    @Test
    public void shouldSetUpdateAndRemoveCredentialWithoutReturningValue() throws Exception {
        ProfileCredentialService service = new ProfileCredentialService(home);
        service.set("SSH_PASSWORD", "a=\"b\\c\nnext");
        assertEquals("a=\"b\\c\nnext", ProfileEnvironmentLoader.load(home).get("SSH_PASSWORD"));
        service.set("SSH_PASSWORD", "updated");
        assertEquals("updated", ProfileEnvironmentLoader.load(home).get("SSH_PASSWORD"));
        assertTrue(service.remove("SSH_PASSWORD"));
        assertFalse(ProfileEnvironmentLoader.load(home).containsKey("SSH_PASSWORD"));
    }

    /** 验证非法环境变量名被拒绝。 */
    @Test
    public void shouldRejectInvalidEnvironmentName() {
        ProfileCredentialService service = new ProfileCredentialService(home);
        assertThrows(IllegalArgumentException.class, () -> service.set("ssh.password", "secret"));
    }

    /** 验证 Profile .env 优先且显式空值仍被视为存在。 */
    @Test
    public void shouldProbeProfileEnvironmentWithoutValue() throws Exception {
        Files.write(
                home.resolve(".env"),
                Collections.singletonList("PATH=\"\""),
                StandardCharsets.UTF_8);
        ProfileCredentialService.CredentialView view =
                new ProfileCredentialService(home).probe("PATH");
        assertEquals("profile_env", view.getSource());
        assertTrue(view.isPresent());
    }

    /** 验证命名 Profile 不回退进程环境。 */
    @Test
    public void shouldFailClosedForNamedProfileProcessEnvironment() throws Exception {
        try (ProfileRuntimeScope.Scope ignored =
                ProfileRuntimeScope.open(
                        "tenant-a", home, Collections.<String, String>emptyMap(), null)) {
            ProfileCredentialService.CredentialView view =
                    new ProfileCredentialService(home).probe("PATH");
            assertEquals("missing", view.getSource());
            assertFalse(view.isPresent());
        }
    }

    /** 验证 POSIX 平台上的 .env 权限为 600。 */
    @Test
    public void shouldUseOwnerOnlyPosixPermissions() throws Exception {
        ProfileCredentialService service = new ProfileCredentialService(home);
        service.set("TOKEN", "secret");
        try {
            Set<PosixFilePermission> permissions =
                    Files.getPosixFilePermissions(home.resolve(".env"));
            assertEquals(
                    new java.util.LinkedHashSet<PosixFilePermission>(
                            java.util.Arrays.asList(
                                    PosixFilePermission.OWNER_READ,
                                    PosixFilePermission.OWNER_WRITE)),
                    permissions);
        } catch (UnsupportedOperationException e) {
            assertTrue(Files.isRegularFile(home.resolve(".env")));
        }
    }
}
