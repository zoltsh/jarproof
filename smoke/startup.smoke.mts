import { expect, smoke, type SmokeContext } from "smoque";

import { packagedJarproof, runJarproof } from "./support/jarproof-runtime.mts";

smoke.suite("packaged CLI startup", { tags: ["cli", "package"] }, async (t: SmokeContext) => {
  const jarproof = await packagedJarproof(t);

  await t.step("reports its identity", async () => {
    const result = await runJarproof(t, jarproof, ["--version"]);
    expect.value(result.stdout.trim()).toMatch(/^jarproof \d+\.\d+\.\d+(?:-[A-Za-z0-9.]+)?$/u);
  });

  await t.step("shows the command surface", async () => {
    const result = await runJarproof(t, jarproof, ["--help"]);
    expect.value(result.stdout).toContain("Find JAR hell before production.");
    expect.value(result.stdout).toContain("check");
    expect.value(result.stdout).toContain("baseline");
    expect.value(result.stdout).toContain("inspect");
    expect.value(result.stdout).toContain("explain");
  });
});
