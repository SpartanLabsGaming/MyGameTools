package com.spartanlabs.gaming.testing.component.networking.command

//region 1. Organization Internal
// 1.2 Spartan Gaming
import com.spartanlabs.gaming.gameobjects.EntityId
import com.spartanlabs.gaming.networking.command.Attack
import com.spartanlabs.gaming.networking.command.ClientCommand
import com.spartanlabs.gaming.networking.command.ClientCommandCodec
import com.spartanlabs.gaming.networking.command.Follow
import com.spartanlabs.gaming.networking.command.MoveDir
import com.spartanlabs.gaming.networking.command.MoveTo
import com.spartanlabs.gaming.networking.command.Stop
import com.spartanlabs.gaming.networking.command.StopAttack
//endregion

//region 2. Intended Function
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass
//endregion

//region 4. Programming Infrastructure and Support
// 4.3 Testing
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
//endregion

/** A stand-in for a game's own [ClientCommand], declared only for the extension-point tests. */
@Serializable
@SerialName("test.buildStructure")
private data class BuildStructure(val builder: EntityId, val kind: String) : ClientCommand

/** Covers [ClientCommandCodec]'s envelope, the standard-command round-trip, and consumer extension. */
class ClientCommandCodecTest {

    private val codec = ClientCommandCodec()

    private fun ClientCommandCodec.roundTrip(command: ClientCommand): ClientCommand =
        decode(encode(command).substringAfter(' ')).getOrThrow()

    @Test
    fun `encode produces a COMMAND-prefixed datagram`() {
        assertTrue(codec.encode(Stop(EntityId(1))).startsWith("${ClientCommandCodec.COMMAND_VERB} "))
    }

    @Test
    fun `every standard command round-trips unchanged`() {
        val commands = listOf(
            MoveTo(EntityId(1), x = 3.5, y = -4.0),
            MoveDir(EntityId(2), angleDegrees = 270),
            Follow(EntityId(3), target = EntityId(4)),
            Stop(EntityId(5)),
            Attack(EntityId(6), target = EntityId(7)),
            StopAttack(EntityId(8)),
        )

        commands.forEach { command -> assertEquals(command, codec.roundTrip(command)) }
    }

    @Test
    fun `an EntityId operand is written as a bare number`() {
        assertTrue(
            codec.encode(MoveTo(EntityId(7), x = 0.0, y = 0.0)).contains("\"actor\":7"),
            "EntityId must serialize transparently as its raw Long"
        )
    }

    @Test
    fun `an unknown discriminator is a decode failure, not a throw`() {
        assertTrue(codec.decode("""{"type":"gametools.teleport","actor":1}""").isFailure)
    }

    @Test
    fun `a payload that is not JSON is a decode failure`() {
        assertTrue(codec.decode("not json at all").isFailure)
    }

    @Test
    fun `a consumer command round-trips when its module is registered on both ends`() {
        val extended = ClientCommandCodec(SerializersModule {
            polymorphic(ClientCommand::class) { subclass(BuildStructure::class) }
        })
        val command = BuildStructure(EntityId(9), kind = "tower")

        assertEquals(command, extended.roundTrip(command))
    }

    @Test
    fun `a consumer command does not decode on a codec built without its module`() {
        val extended = ClientCommandCodec(SerializersModule {
            polymorphic(ClientCommand::class) { subclass(BuildStructure::class) }
        })
        val wire = extended.encode(BuildStructure(EntityId(9), kind = "tower")).substringAfter(' ')

        assertTrue(codec.decode(wire).isFailure, "the standard codec cannot know a consumer type")
    }
}
