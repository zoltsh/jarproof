import { expect, smoke, type SmokeContext } from "smoque";

import {
  fixtureJar,
  packagedJarproof,
  runJarproof,
} from "./support/jarproof-runtime.mts";

smoke.suite("packaged artifact tools", { tags: ["artifact", "package"] }, async (t: SmokeContext) => {
  const jarproof = await packagedJarproof(t);

  await t.step("inspects a packaged fixture", async () => {
    const fixture = await fixtureJar(jarproof, "missing-method-consumer");
    const result = await runJarproof(t, jarproof, [
      "inspect",
      fixture,
      "--format",
      "json",
      "--path-root",
      jarproof.root.toString(),
    ]);

    await expect.command(result).stdoutJsonPath("$.inspectJsonVersion").toBe("1");
    await expect.command(result).stdoutJsonPath("$.classCount").toBe(1);
    await expect.command(result).stdoutJsonPath("$.nestedArchiveCount").toBe(0);
  });

  await t.step("explains a reported diagnostic", async () => {
    const result = await runJarproof(t, jarproof, ["explain", "JP1003"]);
    expect.value(result.stdout).toContain("JP1003 missing method");
    expect.value(result.stdout).toContain("Predicted runtime error: NoSuchMethodError");
  });
});
