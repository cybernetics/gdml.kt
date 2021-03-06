@file:UseSerializers(NumberSerializer::class)

package scientifik.gdml

import kotlinx.serialization.*
import nl.adaptivity.xmlutil.serialization.UnknownChildHandler
import nl.adaptivity.xmlutil.serialization.XML
import nl.adaptivity.xmlutil.serialization.XmlSerialName

@DslMarker
annotation class GDMLApi

@Serializable
@SerialName("gdml")
class GDML {

    //@XmlPolyChildren(["define", "materials", "solids", "structure"])
    val containers: ArrayList<@Polymorphic GDMLContainer> = arrayListOf(
        GDMLDefineContainer(),
        GDMLMaterialContainer(),
        GDMLSolidContainer(),
        GDMLStructure()
    )

    val setup = GDMLSetup()

    val define: GDMLDefineContainer
        get() = (containers.filterIsInstance<GDMLDefineContainer>().firstOrNull()
            ?: GDMLDefineContainer().also { containers.add(0, it) })
    val materials: GDMLMaterialContainer
        get() = (containers.filterIsInstance<GDMLMaterialContainer>().firstOrNull()
            ?: GDMLMaterialContainer().also { containers.add(it) })
    val solids: GDMLSolidContainer
        get() = (containers.filterIsInstance<GDMLSolidContainer>().firstOrNull()
            ?: GDMLSolidContainer().also { containers.add(it) })
    val structure: GDMLStructure
        get() = (containers.filterIsInstance<GDMLStructure>().firstOrNull()
            ?: GDMLStructure().also { containers.add(it) })

    @GDMLApi
    inline fun define(block: GDMLDefineContainer.() -> Unit) {
        define.apply(block)
    }

    @GDMLApi
    inline fun materials(block: GDMLMaterialContainer.() -> Unit) {
        materials.apply(block)
    }

    @GDMLApi
    inline fun solids(block: GDMLSolidContainer.() -> Unit) {
        solids.apply(block)
    }

    @GDMLApi
    inline fun structure(block: GDMLStructure.() -> Unit) {
        structure.apply(block)
    }

    inline fun <reified T : GDMLDefine> getDefine(ref: String): T? =
        containers.filterIsInstance<GDMLDefineContainer>().map { it.get<T>(ref) }.single()

    inline fun <reified T : GDMLMaterial> getMaterial(ref: String): T? =
        containers.filterIsInstance<GDMLMaterialContainer>().map { it.get<T>(ref) }.single()

    inline fun <reified T : GDMLSolid> getSolid(ref: String): T? =
        containers.filterIsInstance<GDMLSolidContainer>().map { it.get<T>(ref) }.single()

    inline fun <reified T : GDMLGroup> getGroup(ref: String): T? =
        containers.filterIsInstance<GDMLStructure>().map { it.get<T>(ref) }.single()

    var world: GDMLGroup
        get() = setup.world?.resolve(this)
            ?: error("The GDML structure does not contain a world volume")
        set(value) {
            //Add world element if it is not registered
            structure {
                if (getGroup(value.name) == null) {
                    content.add(value)
                }
            }
            setup.world = value.ref()
        }

    override fun toString() = format.stringify(this)

    companion object {
        inline operator fun invoke(block: GDML.() -> Unit): GDML = GDML().apply(block)

        private val WARNING_UNKNOWN_CHILD_HANDLER: UnknownChildHandler =
            { location, _, name, candidates ->
                println(
                    "Could not find a field for name $name${if (candidates.isNotEmpty()) candidates.joinToString(
                        prefix = "\n  candidates: "
                    ) else ""} at position $location"
                )
            }

        val format = XML(gdmlModule) {
            autoPolymorphic = true
            indent = 4
            unknownChildHandler = WARNING_UNKNOWN_CHILD_HANDLER
        }



    }
}

inline fun <reified T : GDMLDefine> GDMLRef<T>.resolve(root: GDML): T? = root.getDefine(ref)
inline fun <reified T : GDMLSolid> GDMLRef<T>.resolve(root: GDML): T? = root.getSolid(ref)
inline fun <reified T : GDMLMaterial> GDMLRef<T>.resolve(root: GDML): T? = root.getMaterial(ref)
inline fun <reified T : GDMLGroup> GDMLRef<T>.resolve(root: GDML): T? = root.getGroup(ref)

@Serializable
sealed class GDMLContainer

@Serializable
@SerialName("define")
class GDMLDefineContainer : GDMLContainer() {

    val content = ArrayList<@Polymorphic GDMLDefine>()

    @Transient
    private val cache: MutableMap<String, GDMLDefine?> = HashMap()

    fun getDefine(ref: String): GDMLDefine? = cache.getOrPut(ref) { content.find { it.name == ref } }

    inline operator fun <reified T : GDMLDefine> get(ref: String): T? = getDefine(ref) as? T

    @GDMLApi
    inline fun position(
        name: String,
        x: Number = 0f,
        y: Number = 0f,
        z: Number = 0f,
        block: GDMLPosition.() -> Unit = {}
    ): GDMLPosition {
        return GDMLPosition().apply(block).apply {
            this.name = name
            this.x = x
            this.y = y
            this.z = z
        }.also {
            content.add(it)
        }
    }

    @GDMLApi
    inline fun rotation(
        name: String,
        x: Number = 0f,
        y: Number = 0f,
        z: Number = 0f,
        block: GDMLRotation.() -> Unit = {}
    ): GDMLRotation {
        return GDMLRotation().apply(block).apply {
            this.name = name
            this.x = x
            this.y = y
            this.z = z
        }.also {
            content.add(it)
        }
    }
}

@Serializable
@SerialName("materials")
class GDMLMaterialContainer : GDMLContainer() {

    private val content = ArrayList<@Polymorphic GDMLMaterial>()

    @Transient
    private val cache: MutableMap<String, GDMLMaterial?> = HashMap()

    fun getMaterial(ref: String): GDMLMaterial? = cache.getOrPut(ref) { content.find { it.name == ref } }

    inline operator fun <reified T : GDMLMaterial> get(ref: String): T? = getMaterial(ref) as? T

    companion object {
        val defaultMaterial = GDMLElement("default")
    }
}

@Serializable
@SerialName("solids")
class GDMLSolidContainer : GDMLContainer() {

    val content = ArrayList<@Polymorphic GDMLSolid>()

    @Transient
    private val cache: MutableMap<String, GDMLSolid?> = HashMap()

    fun getSolid(ref: String): GDMLSolid? = cache.getOrPut(ref) { content.find { it.name == ref } }

    inline operator fun <reified T : GDMLSolid> get(ref: String): T? = getSolid(ref) as? T

    @GDMLApi
    fun box(name: String, x: Number = 0f, y: Number = 0f, z: Number = 0f, block: GDMLBox.() -> Unit = {}): GDMLBox {
        val box = GDMLBox(name, x, y, z).apply(block)
        content.add(box)
        return box
    }

    @GDMLApi
    fun tube(name: String, rmax: Number, z: Number, block: GDMLTube.() -> Unit): GDMLTube {
        val tube = GDMLTube(name, rmax, z).apply(block)
        content.add(tube)
        return tube
    }

    @GDMLApi
    fun xtru(name: String, block: GDMLXtru.() -> Unit): GDMLXtru {
        val xtru = GDMLXtru(name).apply(block)
        content.add(xtru)
        return xtru
    }

    @GDMLApi
    fun cone(
        name: String,
        z: Number,
        rmax1: Number,
        rmax2: Number,
        deltaphi: Number,
        block: GDMLCone.() -> Unit
    ): GDMLCone {
        val cone = GDMLCone(name, z, rmax1, rmax2, deltaphi).apply(block)
        content.add(cone)
        return cone
    }

    @GDMLApi
    fun union(
        name: String,
        first: GDMLSolid,
        second: GDMLSolid,
        block: GDMLUnion.() -> Unit
    ): GDMLUnion {
        val union = GDMLUnion(name, first.ref(), second.ref()).apply(block)
        content.add(union)
        return union
    }

    @GDMLApi
    fun intersection(
        name: String,
        first: GDMLSolid,
        second: GDMLSolid,
        block: GDMLIntersection.() -> Unit
    ): GDMLIntersection {
        val intersection = GDMLIntersection(name, first.ref(), second.ref()).apply(block)
        content.add(intersection)
        return intersection
    }

    @GDMLApi
    fun subtraction(
        name: String,
        first: GDMLSolid,
        second: GDMLSolid,
        block: GDMLSubtraction.() -> Unit
    ): GDMLSubtraction {
        val subtraction = GDMLSubtraction(name, first.ref(), second.ref()).apply(block)
        content.add(subtraction)
        return subtraction
    }
}

@Serializable
@SerialName("structure")
class GDMLStructure : GDMLContainer() {

    val content = ArrayList<@Polymorphic GDMLGroup>()

    @Transient
    private val cache: MutableMap<String, GDMLGroup?> = HashMap()

    fun getGroup(ref: String): GDMLGroup? = cache.getOrPut(ref) { content.find { it.name == ref } }

    inline operator fun <reified T : GDMLGroup> get(ref: String): T? = getGroup(ref) as? T

    @GDMLApi
    inline fun volume(
        name: String,
        materialref: GDMLRef<GDMLMaterial>,
        solidref: GDMLRef<GDMLSolid>,
        block: GDMLVolume.() -> Unit = {}
    ): GDMLVolume {
        val res = GDMLVolume(name, materialref, solidref).apply(block)
        content.add(res)
        return res
    }

    @GDMLApi
    inline fun volume(
        name: String,
        material: GDMLMaterial,
        solid: GDMLSolid,
        block: GDMLVolume.() -> Unit = {}
    ): GDMLVolume = volume(name, material.ref(), solid.ref(), block)

    @GDMLApi
    inline fun assembly(
        name: String,
        block: GDMLAssembly.() -> Unit = {}
    ): GDMLAssembly {
        val res = GDMLAssembly(name).apply(block)
        content.add(res)
        return res
    }
}

@Serializable
@SerialName("setup")
class GDMLSetup(
    var name: String = "Default",
    var version: String = "1.0",
    @XmlSerialName("world", "", "")
    var world: GDMLRef<GDMLGroup>? = null
)

@GDMLApi
fun GDML.world(
    name: String = "world",
    block: GDMLAssembly.() -> Unit
) {
    structure {
        assembly(name, block).also { setup.world = it.ref() }
    }
}