import { expect, smoke, type SmokeContext } from "smoque";

import {
  missingMethodArgs,
  packagedJarproof,
  runJarproof,
} from "./support/jarproof-runtime.mts";

smoke.suite("packaged linkage check", { tags: ["analysis", "package"] }, async (t: SmokeContext) => {
  const jarproof = await packagedJarproof(t);

  await t.step("predicts a missing runtime method", async () => {
    const args = await missingMethodArgs(jarproof, "missing-method-api-v2");
    const result = await runJarproof(t, jarproof, ["check", ...args, "--format", "json"], {
      check: false,
    });

    expect.value(result.exitCode).toBe(1);
    await expect.command(result).stdoutJsonPath("$.findings.0.code").toBe("JP1003");
    await expect.command(result).stdoutJsonPath("$.findings.0.predictedError").toBe("NoSuchMethodError");
    await expect.command(result).stdoutJsonPath("$.summary.error").toBe(1);
  });

  await t.step("accepts the matching runtime API", async () => {
    const args = await missingMethodArgs(jarproof, "missing-method-api-v1");
    const result = await runJarproof(t, jarproof, ["check", ...args, "--format", "json"]);

    await expect.command(result).stdoutJsonPath("$.findings").toEqual([]);
    await expect.command(result).stdoutJsonPath("$.summary.total").toBe(0);
  });
});
