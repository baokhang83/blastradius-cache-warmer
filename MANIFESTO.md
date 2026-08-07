## ---

**Product Specification: Blastradius Cache Warmer**

A performance-focused **Maven Core Extension** built to eliminate the initialization, dependency-downloading, and environment-setup overhead of Java/Kotlin monorepos in ephemeral CI pipelines (e.g., GitHub Actions, GitLab CI).

## **1\. The Core Problem It Solves**

While Test Impact Analysis (TIA) engines like blastradius successfully cut test execution times, modern CI pipelines still waste **2 to 5 minutes per run** on clean agents doing three things:

> 1. Downloading and decompressing massive, monolithic \~/.m2/repository cache blocks (often 2GB–5GB+).  
> 2. Network and disk I/O bottlenecks from checking and pulling down sibling project modules.  
> 3. Re-compiling unmodified upstream code because the local compiler state was wiped.

## **2\. Architectural Approach: Why a Core Extension?**

Standard Maven plugins run during the build lifecycle, which is too late to optimize dependency resolution. This tool is built as an **AbstractMavenLifecycleParticipant** registered inside .mvn/extensions.xml.

It intercepts execution during the **Pre-Resolution Phase** (immediately after reading the root pom.xml but before resolving artifacts), giving it the power to dynamically modify the local environment and selectively warm folders.

                  `[ mvn clean install ]`  
                            `│`  
                            `▼`  
           `► [ CacheWarmerExtension Triggers ] ◄`  
         `Reads git diff + local .blastradius map.`  
      `Downloads ONLY the target slices from cloud storage.`  
                            `│`  
                            `▼`  
              `[ Maven Dependency Resolution ]`  
         `Finds warm local .m2 artifacts; skips network.`  
                            `│`  
                            `▼`  
                `[ Maven Compilation Layer ]`  
          `Finds warm target/ bytecode; skips compiler.`

## **3\. The 3-Tier Granular Caching Strategy**

Instead of saving one massive global cache, the extension splits storage into lightweight, micro-targeted slices keyed by cryptographic hashes of a module's source tree:

## **A. Sibling Bytecode (\*\*/target/classes/)**

> * **Concept:** If Module A depends on Module B, and Module B hasn't changed, there is no reason to recompile Module B.  
> * **Action:** The extension pulls a micro-tarball of Module B's pre-compiled bytecode directly into its local /target/ directory, fooling Maven into treating it as already updated.

## **B. Segmented Third-Party Libraries (\~/.m2/repository/)**

> * **Concept:** A developer modifying an isolated billing module doesn't need the hundreds of heavy dependencies (like AWS SDKs or frontend engines) used by other modules in the repo.  
> * **Action:** It parses the specific module's dependency tree and down-selects the cloud cache pull to *only* the third-party JARs required by the active blast radius boundary.

## **C. Incremental Compiler State (target/maven-status/)**

> * **Concept:** The maven-compiler-plugin keeps state maps to track incremental changes. If these maps are missing on a clean CI runner, Maven forces a full rebuild.  
> * **Action:** It restores these state files alongside the bytecode to maintain incremental compiler tracking continuity across separate cloud runners.

## **4\. CI Performance Benchmark Comparison**

| Metric / Lifecycle Phase | Traditional Monolithic CI | Blastradius Cache Warmer |
| :---- | :---- | :---- |
| **Cache Sync Mechanics** | Downloads single 3GB global zip. | Fetches specific 150MB module slices. |
| **Network & I/O Overhead** | 2.5 to 4 minutes. | **Less than 10 seconds.** |
| **Upstream Compilation** | Rebuilds all sibling modules. | Restores pre-compiled target bytecode. |
| **Dependency Auditing** | Downloads missing snapshot JARs. | Pulls deterministic dependency map. |

---

