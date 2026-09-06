//region 4. Programming Infrastructure and Support
// 4.4 Profiling
import org.gradle.api.services.BuildService
import org.gradle.api.services.BuildServiceParameters
//endregion

/**
 * A single-permit Gradle build service. Every test task that starts a `GameServer` binds the
 * one fixed common UDP port, so no two such tasks may run at the same time - each declares
 * this service via `usesService(...)` and Gradle serializes them.
 */
abstract class GameServerPortsLock : BuildService<BuildServiceParameters.None>
