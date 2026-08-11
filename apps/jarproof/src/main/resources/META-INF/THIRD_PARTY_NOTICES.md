# Third-party notices

jarproof itself is licensed under the Apache License, Version 2.0 (see
`LICENSE`). This file records the third-party software redistributed inside
jarproof's published artifacts and the notices those licenses require.

Which artifact contains what:

| Artifact | Contains |
|---|---|
| `jarproof-cli-<version>.jar` (uber JAR) | picocli, ASM |
| `jarproof-<version>-<platform>` (native binary) | picocli, ASM, and GraalVM CE runtime and JDK components |
| Both | JDK API signature data (see "JDK API signature data" below) |

---

## picocli

- Coordinate: `info.picocli:picocli:4.7.6`
- Homepage: https://picocli.info
- Copyright 2017 Remko Popma
- License: Apache License, Version 2.0

Licensed under the Apache License, Version 2.0 (the "License"); you may not use
this file except in compliance with the License. You may obtain a copy of the
License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software distributed
under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR
CONDITIONS OF ANY KIND, either express or implied. See the License for the
specific language governing permissions and limitations under the License.

The full text of the Apache License, Version 2.0 is in `LICENSE`, which is the
same license jarproof itself is released under. The picocli 4.7.6 artifact
ships no `NOTICE` file, so Apache-2.0 section 4(d) imposes no further
attribution. picocli class files are included unmodified and unrelocated.

---

## ASM

- Coordinate: `org.ow2.asm:asm:9.9`
- Homepage: https://asm.ow2.io
- License: BSD-3-Clause

ASM class files are included unmodified and unrelocated. BSD-3-Clause requires
that binary redistributions reproduce the following notice, conditions, and
disclaimer:

    ASM: a very small and fast Java bytecode manipulation framework
    Copyright (c) 2000-2011 INRIA, France Telecom
    All rights reserved.

    Redistribution and use in source and binary forms, with or without
    modification, are permitted provided that the following conditions
    are met:
    1. Redistributions of source code must retain the above copyright
       notice, this list of conditions and the following disclaimer.
    2. Redistributions in binary form must reproduce the above copyright
       notice, this list of conditions and the following disclaimer in the
       documentation and/or other materials provided with the distribution.
    3. Neither the name of the copyright holders nor the names of its
       contributors may be used to endorse or promote products derived from
       this software without specific prior written permission.

    THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
    AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
    IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
    ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE
    LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
    CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
    SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
    INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
    CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
    ARISING OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY
    OF SUCH DAMAGE.

---

## GraalVM Community Edition (native binaries only)

The native binaries are compiled by GraalVM Community Edition Native Image and
statically incorporate GraalVM CE runtime components (Substrate VM) together
with the subset of the GraalVM CE Java class library that the program reaches.

- Product: GraalVM Community Edition 25.0.2
- Homepage: https://www.graalvm.org
- License: GNU General Public License, version 2, with the Classpath Exception

The Classpath Exception expressly permits linking these components with
independent modules to produce an executable and distributing that executable
under terms of your choice, which is why the jarproof native binaries are
distributed under the Apache License, Version 2.0. The definitive third-party
license listing for the GraalVM CE build used is the `THIRD_PARTY_LICENSE` file
shipped in that GraalVM CE distribution.

The native binaries link no third-party native libraries statically. They
dynamically link only libraries provided by the host operating system (on
macOS: `libSystem`, `libobjc`, `libz`, `CoreFoundation`, `Foundation`; on
Linux: `libc`, `libdl`, `libpthread`, `libz`).

The uber JAR contains no GraalVM code.

---

## JDK API signature data

Both artifacts embed five files of Java platform API signature data
(`jdk-symbols-8.bin`, `-11`, `-17`, `-21`, `-25`), derived from the `lib/ct.sym`
signature archive of a GraalVM Community Edition JDK.

These files contain only factual API metadata — type and member names,
descriptors, access flags, and inheritance relationships. They contain no
bytecode, no method bodies, no constant values, no documentation, and no source
code.

They are generated deterministically from the `lib/ct.sym` signature archive
of GraalVM Community Edition 25.0.2, the repository's pinned managed
toolchain. Each resource records its target release, class count, and exact
generating runtime version in its header. The committed extraction code reads
`ct.sym`; the test-scope generator rebuilds the compressed resources; and the
freshness tests byte-compare all five generated files with the committed
copies. The gzip stream uses a zero modification time and production code
defines every ordering decision.

The resources contain exhaustive factual interface metadata: type and member
names, descriptors, access flags, module ownership, and inheritance and nest
relationships. They contain no bytecode, method bodies, field values,
documentation, source code, annotations, resources, or `ct.sym` bytes.

jarproof's position is that this exhaustive factual API metadata is not a
copyrightable work and therefore does not require or receive a license from
the JDK's copyright holders. The project recognizes that compilation rights,
derivative-work analysis, and database rights can be assessed differently by
different jurisdictions. If a rights holder disagrees, open an issue with the
project; the bundled resources can be removed and the existing `--jdk <path>`
mode used instead without redesigning the verifier.

Oracle and Java are registered trademarks of Oracle and/or its affiliates.
jarproof is not affiliated with, endorsed by, or certified by Oracle, the
OpenJDK project, or the GraalVM project.
