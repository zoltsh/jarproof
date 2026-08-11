import { access, readFile } from "node:fs/promises";
import { join } from "node:path";

import {
  type CommandOptions,
  type CommandResult,
  type PathRef,
  type SmokeContext,
} from "smoque";

export interface JarproofRuntime {
  readonly command: string;
  readonly jar: string;
  readonly root: PathRef;
}

export async function packagedJarproof(t: SmokeContext): Promise<JarproofRuntime> {
  const root = t.repoRoot();
  const java = await t.tools.java({ minVersion: 17 });
  const jar = await memberJar(root.path("apps", "jarproof"));
  return { command: java.command, jar, root };
}

export async function fixtureJar(runtime: JarproofRuntime, member: string): Promise<string> {
  return await memberJar(runtime.root.path("fixtures", member));
}

export async function runJarproof(
  t: SmokeContext,
  runtime: JarproofRuntime,
  args: string[],
  options: CommandOptions = {},
): Promise<CommandResult> {
  return await t.cmd(runtime.command, ["-jar", runtime.jar, ...args], {
    cwd: runtime.root,
    timeout: "45s",
    ...options,
  });
}

export async function missingMethodArgs(
  runtime: JarproofRuntime,
  apiMember: string,
): Promise<string[]> {
  return [
    "--application",
    await fixtureJar(runtime, "missing-method-consumer"),
    "--classpath",
    await fixtureJar(runtime, apiMember),
    "--target-java",
    "17",
    "--path-root",
    runtime.root.toString(),
  ];
}

async function memberJar(member: string): Promise<string> {
  const manifest = await readFile(join(member, "zolt.toml"), "utf8");
  const name = /^name = "([^"]+)"/mu.exec(manifest)?.[1];
  const version = /^version = "([^"]+)"/mu.exec(manifest)?.[1];
  if (name === undefined || version === undefined) {
    throw new Error(`Could not read the project identity from ${join(member, "zolt.toml")}`);
  }
  const jar = join(member, "target", `${name}-${version}.jar`);
  await access(jar);
  return jar;
}
