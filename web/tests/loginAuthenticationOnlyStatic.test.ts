import { readFileSync } from "node:fs";
import { strict as assert } from "node:assert";

const loginView = readFileSync(
  new URL("../src/views/LoginView.vue", import.meta.url),
  "utf8",
);

assert.ok(
  !loginView.includes("/api/workspace-config/bootstrap-dashboard-token"),
  "login page must not call an unauthenticated localhost bootstrap endpoint",
);

assert.ok(
  /if \(!await exchangeDashboardSession\(key\)\)[\s\S]*errorMsg\.value = t\("login\.invalidToken"\)/.test(
    loginView,
  ),
  "invalid dashboard credentials should fail closed without a local bootstrap fallback",
);
