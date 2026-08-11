import { expect, smoke, type SmokeContext } from "smoque";

import {
  missingMethodArgs,
  packagedJarproof,
  runJarproof,
} from "./support/jarproof-runtime.mts";

smoke.suite("packaged baseline workflow", { tags: ["baseline", "package"] }, async (t: SmokeContext) => {
  const jarproof = await packagedJarproof(t);
  const work = await t.tempDir("jarproof-baseline-smoke");
  const baseline = work.path("jarproof-baseline.json");
  const args = await missingMethodArgs(jarproof, "missing-method-api-v2");

  await t.step("records the existing finding", async () => {
    const result = await runJarproof(t, jarproof, ["baseline", ...args, "--out", baseline]);

    expect.value(result.stderr).toContain("wrote 1 accepted finding");
    await expect.file(baseline).jsonPath("$.fingerprints.0").toExist();
  });

  await t.step("suppresses the recorded finding", async () => {
    const result = await runJarproof(
      t,
      jarproof,
      ["check", ...args, "--baseline", baseline, "--format", "json"],
    );

    expect.value(result.stderr).toContain("suppressed 1 accepted finding");
    await expect.command(result).stdoutJsonPath("$.findings").toEqual([]);
    await expect.command(result).stdoutJsonPath("$.summary.total").toBe(0);
  });
});
