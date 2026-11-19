package io.embodit.bandagewrap.ui.scene

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.Color
import com.pico.spatial.core.container.SpatialViewContent
import com.pico.spatial.core.ecs.CollisionComponent
import com.pico.spatial.core.ecs.DirectionalLightComponent
import com.pico.spatial.core.ecs.Entity
import com.pico.spatial.core.ecs.HoverEffectComponent
import com.pico.spatial.core.ecs.InteractableComponent
import com.pico.spatial.core.ecs.ModelEntity
import com.pico.spatial.core.ecs.SceneUpdateContext
import com.pico.spatial.core.ecs.System
import com.pico.spatial.core.ecs.TransformComponent
import com.pico.spatial.core.ecs.resource.Material
import com.pico.spatial.core.ecs.resource.MeshResource
import com.pico.spatial.core.ecs.resource.PhysicsMaterialResource
import com.pico.spatial.core.ecs.resource.ShapeResource
import com.pico.spatial.core.math.Color4
import com.pico.spatial.core.math.Quat
import com.pico.spatial.core.math.Vector3
import com.pico.spatial.ui.foundation.content.SpatialViewAttachments
import io.embodit.bandagewrap.domain.Align
import io.embodit.bandagewrap.domain.ArmLayout
import io.embodit.bandagewrap.domain.ArmModel
import io.embodit.bandagewrap.domain.ArmParams
import io.embodit.bandagewrap.domain.ArmPose
import io.embodit.bandagewrap.domain.ArmSide
import io.embodit.bandagewrap.domain.Girth
import io.embodit.bandagewrap.domain.HandJoints
import io.embodit.bandagewrap.domain.HeadPose
import io.embodit.bandagewrap.domain.Quat4
import io.embodit.bandagewrap.domain.SteadyLock
import io.embodit.bandagewrap.domain.TurnGeom
import io.embodit.bandagewrap.domain.Upright
import io.embodit.bandagewrap.domain.Vec3
import io.embodit.bandagewrap.domain.WrapBuilder
import io.embodit.bandagewrap.domain.Wraps
import io.embodit.bandagewrap.domain.Yaw
import io.embodit.bandagewrap.platform.BwLog
import io.embodit.bandagewrap.platform.input.Controllers
import io.embodit.bandagewrap.platform.tracking.Device
import io.embodit.bandagewrap.platform.tracking.HandTracker
import io.embodit.bandagewrap.platform.tracking.HeadTracker
import io.embodit.bandagewrap.ui.BwController
import io.embodit.bandagewrap.ui.BwUiState
import io.embodit.bandagewrap.ui.Screen
import io.embodit.bandagewrap.ui.input.TargetResolver
import io.embodit.bandagewrap.ui.theme.BwColors
import io.embodit.bandagewrap.ui.window.WindowCtl
import java.util.Locale
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** The scene of the open Stage (debug hooks and the frame system talk to it). Main thread only. */
internal object ActiveScene {
    var current: BwScene? = null
}

/** The one per-frame ECS system of the app (`registerSystem` in BwScreen). */
class BwFrameSystem : System() {
    override fun update(context: SceneUpdateContext) {
        ActiveScene.current?.onFrame(context)
    }
}

object PanelIds {
    /** "打开面板": shown only while the window is closed / minimised, above the arm. */
    const val CHIP = "chip"

    /** "准备中 / 平视前方 / 看正前方": one line on the line of sight while the window waits. */
    const val HINT = "hint"

    /** The turn label (design panels[1] turnLabel): over the limb at the current turn, yaw-only facing. */
    const val LABEL = "label"
    val pool = listOf(CHIP, HINT, LABEL)
}

/** 3D targets of the app (board-input resolver by entity name). */
enum class Target(val entityName: String) {
    ARM("hit:arm"),
    WRIST_HANDLE("hit:wristHandle"),
    ELBOW_HANDLE("hit:elbowHandle");

    companion object {
        fun ofName(n: String): Target? = entries.firstOrNull { it.entityName == n }
    }
}

/**
 * The arm on the Stage (design entities3d): rig (yaw only, at the arm's pivot) → poseNode (arm-local → rig) → the arm
 * mesh, one entity per laid turn, the turn being laid (rebuilt as it grows), the roll with its arrow, the next-turn line.
 * All per-frame work is here ([onFrame]): head, stick, playback ([BwController.tick]), the roll, the label. Nothing
 * per-frame goes through Compose.
 */
class BwScene {
    val panelShown = mutableStateMapOf<String, Boolean>()
    val bindTick = mutableIntStateOf(0)

    /** The label's two lines (turn n / N and the short cue), read by the attachment panel. */
    val labelTop = mutableStateOf("")
    val labelSub = mutableStateOf("")

    private val main = Handler(Looper.getMainLooper())
    private var scope: CoroutineScope? = null
    private val head = HeadTracker()
    private val controllers = Controllers()
    private val hands = HandTracker()
    private var content: SpatialViewContent? = null
    private var stageRoot: Entity? = null
    private var panels: PanelRig? = null
    private var shared: SharedResources? = null
    private var kit: Kit? = null
    private var appContext: Context? = null
    private var attached = false
    private var paused = false
    private var startMs = 0L

    // ---- entities
    private var rig: Entity? = null
    private var poseNode: Entity? = null
    private var armEnt: ModelEntity? = null
    private var armHit: Entity? = null
    private val turnEnts = ArrayList<ModelEntity>()
    private val turnShown = ArrayList<Boolean>()
    private var current: ModelEntity? = null
    private var currentMesh: MeshResource? = null
    private var currentKey = ""
    private var rollEnt: Entity? = null
    private val rollParts = ArrayList<ModelEntity>()
    private var arrowEnt: ModelEntity? = null
    private var nextLine: ModelEntity? = null
    private var nextLineMesh: MeshResource? = null
    private var nextLineFor = -2
    private var wristHandle: ModelEntity? = null
    private var elbowHandle: ModelEntity? = null
    private var geomMaterials: Pair<Material, Material>? = null

    // ---- geometry
    private var arm: ArmModel? = null
    private var turns: List<TurnGeom> = emptyList()
    private var builtEpoch = -1
    private var buildingEpoch = -1
    private var buildJob: Job? = null
    private var builtWrap: String? = null
    private var shownXray: Boolean? = null
    private var ghostShown: Boolean? = null

    // ---- head and placement
    private var pose = HeadPose(Vec3(0f, 1.2f, 0f), Quat4.IDENTITY)
    private var hasHead = false
    private var rigPose: Upright? = null
    private var placeEpoch = 0
    private var placedUpright: Boolean? = null
    private var placedSide: ArmSide? = null
    private var spinEpoch = 0
    private var spinStart = 0L
    private var spinBase = 0f
    private var lastFrameMs = 0L
    private var frames = 0L
    private var startupLogs = 0
    private var lastErrorMs = 0L
    private var lastStickLog = 0L

    // ---- drag
    private var dragTarget: Target? = null
    private var dragStart: Upright? = null
    private var dragPoseStart: ArmPose? = null

    // ---- own arm
    private val steady = SteadyLock()
    private var supportAskedMs = 0L

    /** Debug `pose …`: injected joints of the arm's hand (replace the tracker, design HandPoseSource.Injected). */
    @Volatile var injected: HandJoints? = null

    /** Debug `yaw <deg>` / QA: an extra turn of the arm's rig. */
    var debugYawDeg = 0f

    // =========================================================================================== lifecycle

    fun attach(content: SpatialViewContent, attachments: SpatialViewAttachments, context: Context) {
        if (attached || stageRoot != null) detach()
        appContext = context.applicationContext
        startMs = SystemClock.elapsedRealtime()
        this.content = content
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        val root = Entity().named("stageRoot")
        content.addEntity(root)
        // an entity added to the SpatialView is not at the Stage origin (Spatial Ruler): move the root once
        val offset = root.convertPositionFrom(Vector3.ZERO, null)
        val rotation = root.convertRotationFrom(Quat.identity(), null)
        root.components[TransformComponent::class.java]?.apply {
            setPosition(offset)
            setQuaternion(rotation)
        }
        stageRoot = root
        val pr = PanelRig(root) { id, shown -> panelShown[id] = shown }
        panels = pr
        pr.world(PanelIds.CHIP, CHIP_W, CHIP_H, PANEL_SCALE).maxPitch = 0.45f
        pr.world(PanelIds.HINT, HINT_W, HINT_H, PANEL_SCALE).maxPitch = 0f
        // the label: yaw only (D10), 152 x 64 dp at 0.53 mm/dp (design spatialLayout.turnLabel)
        pr.world(PanelIds.LABEL, LABEL_W, LABEL_H, LABEL_SCALE).maxPitch = 0f
        head.start()
        controllers.start()
        ActiveScene.current = this
        attached = true
        BwController.quitHook = { releaseAll() }
        bindPanels(attachments)
        scope?.launch { buildStatic(root) }
        BwLog.i("SCENE attached root offset=${BwLog.pos(offset.domain())}")
    }

    private suspend fun buildStatic(root: Entity) {
        delay(80)
        val sh = SharedResources()
        shared = sh
        val k = withContext(Dispatchers.IO) { Kit(sh) }
        kit = k
        // light: the system's ambient + one warm key light from above and behind the head (design visual.lighting, B24)
        val sun = Entity().named("sun")
        sun.components.set(DirectionalLightComponent(Color4(1f, 0.96f, 0.9f, 1f), SUN_LUX, false))
        root.addChild(sun)
        sun.setLocal(rotation = Quat4.fromTo(Vec3(0f, 0f, -1f), Vec3(0.25f, -0.8f, -0.55f).normalized()))
        val r = Entity().named("armRig")
        root.addChild(r)
        r.enabled = false
        rig = r
        val pn = Entity().named("poseNode")
        r.addChild(pn)
        poseNode = pn
        val hit = Entity().named(Target.ARM.entityName)
        pn.addChild(hit)
        armHit = hit
        rollEnt = Entity().named("roll").also { pn.addChild(it); it.enabled = false }
        arrowEnt = model(k.arrow, k.orange, "rollArrow", pn).also { it.enabled = false }
        wristHandle = model(k.ball, k.handle, Target.WRIST_HANDLE.entityName, root).also { it.enabled = false; collider(it, Vec3(0.06f, 0.06f, 0.06f)) }
        elbowHandle = model(k.ball, k.handle, Target.ELBOW_HANDLE.entityName, root).also { it.enabled = false; collider(it, Vec3(0.06f, 0.06f, 0.06f)) }
        BwLog.i("SCENE static built")
    }

    private fun collider(e: Entity, size: Vec3) {
        // gate:allow resource-global the shape belongs to this one entity for its whole life; never reused across entities
        e.components.set(CollisionComponent(collisionShape = listOf(ShapeResource.createBox(Vector3(size.x, size.y, size.z))), physicsMaterial = PhysicsMaterialResource()))
        e.components.set(InteractableComponent())
        runCatching { e.components.set(HoverEffectComponent()) }
    }

    fun pause() {
        if (paused || !attached) return
        paused = true
        head.stop()
        controllers.stop()
        hands.stop()
        BwLog.i("SCENE paused")
    }

    fun resume() {
        if (!paused || !attached) return
        paused = false
        head.start()
        controllers.start()
        lastFrameMs = 0L
        BwLog.i("SCENE resumed")
    }

    fun bindPanels(attachments: SpatialViewAttachments) {
        val pr = panels ?: return
        if (!pr.bind(attachments, PanelIds.pool, SystemClock.elapsedRealtime())) {
            main.removeCallbacks(rebind)
            main.postDelayed(rebind, 250L)
        }
    }

    private val rebind = Runnable { if (stageRoot != null) bindTick.intValue++ }

    private fun releaseAll() {
        head.stop()
        controllers.stop()
        hands.stop()
    }

    fun detach() {
        if (!attached && stageRoot == null) return
        attached = false
        if (ActiveScene.current === this) ActiveScene.current = null
        BwController.quitHook = null
        main.removeCallbacksAndMessages(null)
        buildJob?.cancel()
        scope?.cancel()
        scope = null
        releaseAll()
        panelShown.clear()
        runCatching { panels?.release() }
        runCatching { stageRoot?.destroy(true) }
        clearGeometryRefs()
        shared?.close()
        shared = null
        kit = null
        stageRoot = null
        panels = null
        rig = null
        builtEpoch = -1
        buildingEpoch = -1
        BwLog.i("SCENE detached")
    }

    private fun clearGeometryRefs() {
        turnEnts.clear()
        turnShown.clear()
        current = null
        currentMesh = null
        nextLine = null
        nextLineMesh = null
        armEnt = null
    }

    // =========================================================================================== per frame

    fun onFrame(context: SceneUpdateContext) {
        val root = stageRoot ?: return
        if (root.scene == null || context.scene != root.scene) return
        val now = SystemClock.elapsedRealtime()
        val dt = if (lastFrameMs == 0L) 0f else (now - lastFrameMs) / 1000f
        lastFrameMs = now
        frames++
        head.pose()?.let {
            // a first frame of all zeros + identity is "not tracking yet" (hand-sign teacher lesson)
            val zero = it.position.length < 1e-3f && abs(it.rotation.w) > 0.99999f
            if (!zero) {
                pose = it
                hasHead = true
            }
        }
        val pr = panels ?: return
        pr.onFrame(now)
        pr.stowBehind(pose)
        val pitch = if (hasHead) Math.toDegrees(pose.pitch.toDouble()).toFloat() else null
        WindowCtl.stepGate(now, pitch, pose.yaw(0f))
        BwController.onRecentering(WindowCtl.recentering)
        if (hasHead || now - startMs > NO_HEAD_MS) BwController.onReady()
        if (!attached || paused) return
        val ui = BwController.state.value
        try {
            if (kit != null) {
                stepGeometry(ui)
                stepPlacement(ui, now)
                stepStick(ui, dt, now)
                stepSpin(now)
                stepAlign(ui, now)
                BwController.tick(dt)
                writeRig()
                stepBandage(BwController.state.value)
            }
            placePanels(BwController.state.value, now)
        } catch (t: Throwable) {
            if (now - lastErrorMs > 2000) {
                lastErrorMs = now
                BwLog.e("FRAME step failed: $t", t)
            }
        }
        pr.faceViewer(pose.position)
        stepLogs(ui, now)
    }

    // ---- geometry (wrap / side / own arm): built off the main thread, entities a frame apart (ANR rule)

    private fun paramsFor(ui: BwUiState): ArmParams {
        val mine = if (ui.onMyArm || ui.screen == Screen.MANUAL || ui.screen == Screen.ALIGN) BwController.myArm else null
        val s = mine?.second ?: 1f
        val forearm = ui.prefs.forearmCm?.let { it / 100f } ?: if (mine != null) 0.25f * s else ArmParams.DEFAULT_FOREARM
        return ArmParams(
            forearmLen = forearm,
            girth = ui.prefs.girth ?: Girth.MID,
            handScale = s,
            side = ui.prefs.side,
            upright = ui.wrapId == "L7" && !ui.onMyArm,
        )
    }

    private fun stepGeometry(ui: BwUiState) {
        val epoch = BwController.geometryEpoch
        if (epoch == builtEpoch || epoch == buildingEpoch) return
        buildingEpoch = epoch
        val params = paramsFor(ui)
        val wrap = ui.wrapId?.let { Wraps.byId(it) }
        buildJob?.cancel()
        buildJob = scope?.launch {
            val t0 = SystemClock.elapsedRealtime()
            val (a, geo, armMesh) = withContext(Dispatchers.Default) {
                val am = ArmModel(params)
                val g = if (wrap != null) WrapBuilder.build(wrap, am) else emptyList()
                Triple(am, g, Meshes.arm(am))
            }
            val t1 = SystemClock.elapsedRealtime()
            val k = kit ?: return@launch
            val strips = withContext(Dispatchers.Default) { geo.map { Meshes.strip(it) } }
            if (BwController.geometryEpoch != epoch) return@launch
            installGeometry(k, a, geo, armMesh, strips, wrap?.id)
            builtEpoch = epoch
            BwLog.i(String.format(Locale.US, "GEOM built wrap=%s turns=%d side=%s L=%.2f girth=%s s=%.2f upright=%s solve=%d ms total=%d ms", wrap?.id, geo.size, params.side, params.forearmLen, params.girth, params.handScale, params.upright, t1 - t0, SystemClock.elapsedRealtime() - t0))
            if (wrap != null) BwController.onBuilt(wrap.id, geo.map { it.kind })
        }
    }

    private suspend fun installGeometry(k: Kit, a: ArmModel, geo: List<TurnGeom>, armMesh: MeshBuilder, strips: List<MeshBuilder>, wrapId: String?) {
        val pn = poseNode ?: return
        // old entities out
        turnEnts.forEach { runCatching { it.destroy(true) } }
        turnEnts.clear()
        turnShown.clear()
        runCatching { current?.destroy(true) }
        current = null
        currentMesh = null
        currentKey = ""
        runCatching { nextLine?.destroy(true) }
        nextLine = null
        nextLineMesh = null
        nextLineFor = -2
        runCatching { armEnt?.destroy(true) }
        rollParts.forEach { runCatching { it.destroy(true) } }
        rollParts.clear()
        shownXray = null
        ghostShown = null
        arm = a
        turns = geo
        builtWrap = wrapId
        // gate:allow geometry 桌上手臂用参数化网格（设计包 entities3d.tableArm 退路）：PICO AI 生成服务在 mac1 未登录（2026-11-18
        // pico-auth-cli-cn status = not_logged_in），写实候选没生成；绷带按同一参数面计算，零缝隙。登录后可换写实模型（needsCheck N11）
        val am = k.sh.keep(armMesh.build("arm"))
        armEnt = model(am, k.skin, "armMesh", pn)
        // arm collider: the limb's bounds (drag the table arm, T5)
        val (mn, mx) = a.bounds()
        armHit?.let { h ->
            h.setLocal(position = (mn + mx) * 0.5f)
            h.components.remove(CollisionComponent::class.java)
            collider(h, (mx - mn) + Vec3(0.02f, 0.02f, 0.02f))
        }
        delay(16)
        strips.forEachIndexed { i, b ->
            val e = model(k.sh.keep(b.build("turn$i")), k.cream, "turn:$i", pn)
            e.enabled = false
            turnEnts += e
            turnShown += false
            if (i % 4 == 3) delay(8)
        }
        if (geo.isNotEmpty()) {
            val w = geo.first().width
            val cm = k.sh.keep(MeshBuilder(8).build("current"))
            currentMesh = cm
            current = model(cm, k.warm, "currentTurn", pn).also { it.enabled = false }
            rollEnt?.let { r ->
                rollParts += model(k.sh.keep(Meshes.roll(ROLL_R, w).build("roll")), k.rollSide, "rollBody", r)
                rollParts += model(k.sh.keep(Meshes.rollEnds(ROLL_R, w).build("rollEnds")), k.rollEnd, "rollEnds", r)
            }
            val lm = k.sh.keep(MeshBuilder(8).build("nextLine"))
            nextLineMesh = lm
            nextLine = model(lm, k.orange, "nextLine", pn).also { it.enabled = false }
        }
        rig?.on(true)
        BwLog.i("INSTANCE arm=parametric tris=${am.let { armMesh.indices.size / 3 }} turns=${geo.size} maxLayer=${geo.maxOfOrNull { it.maxLayer } ?: 0} materials=PBR opaque (skin, cream, warm) + unlit orange")
    }

    // ---- placement (design spatialLayout.tableArm, D9: from the head at the moment the window opens, yaw only)

    private fun stepPlacement(ui: BwUiState, now: Long) {
        val a = arm ?: return
        val wantUpright = a.p.upright
        val sideNow = a.p.side
        val epochChanged = WindowCtl.placeEpoch != placeEpoch
        val poseChanged = (placedUpright != null && placedUpright != wantUpright) || (placedSide != null && placedSide != sideNow)
        if ((epochChanged && (hasHead || now - startMs > NO_HEAD_MS)) || poseChanged || (rigPose == null && (hasHead || now - startMs > NO_HEAD_MS))) {
            placeEpoch = WindowCtl.placeEpoch
            placedUpright = wantUpright
            placedSide = sideNow
            val up = ArmLayout.place(placeHead(), a)
            rigPose = up
            spinBase = 0f
            val wrist = ArmLayout.toStage(up, a, Vec3.ZERO)
            BwLog.i(String.format(Locale.US, "PLACE rig=%s yaw=%.1f wrist=%s dist=%.2f bearing=%.1f° down=%.1f° upright=%s side=%s head=%s nohead=%s", BwLog.pos(up.position), Math.toDegrees(up.yaw.toDouble()), BwLog.pos(wrist), ArmLayout.flatDist(wrist, pose.position), ArmLayout.bearingDeg(pose, up.position), ArmLayout.downDeg(pose.position, up.position), wantUpright, sideNow, BwLog.pos(pose.position), !hasHead))
        }
    }

    private fun placeHead(): HeadPose =
        if (debugYawDeg == 0f) pose else HeadPose(pose.position, Quat4.axisAngle(Vec3.UP, Math.toRadians(debugYawDeg.toDouble()).toFloat()) * pose.rotation)

    /** Rig + poseNode transforms: the table arm (rig at the pivot, yaw + spin) or your own arm (wrist + its rotation). */
    private fun writeRig() {
        val r = rig ?: return
        val pn = poseNode ?: return
        val a = arm ?: return
        val ui = BwController.state.value
        val mine = BwController.myArm
        val ownArm = (ui.onMyArm || ui.screen == Screen.MANUAL || ui.screen == Screen.ALIGN) && mine != null
        if (ownArm) {
            val p = mine!!.first
            r.setLocal(position = p.wrist, rotation = p.rotation)
            pn.setLocal(position = Vec3.ZERO, rotation = Quat4.IDENTITY)
        } else {
            val rp = rigPose ?: return
            r.setLocal(position = rp.position, rotation = Quat4.axisAngle(Vec3.UP, rp.yaw + spinBase))
            val q = ArmLayout.poseRotation(a.p.upright)
            pn.setLocal(position = q.rotate(-a.pivot()), rotation = q)
        }
    }

    /** Arm-local → Stage with the rig as it is drawn this frame. */
    private fun stage(local: Vec3): Vec3 {
        val a = arm ?: return local
        val ui = BwController.state.value
        val mine = BwController.myArm
        if ((ui.onMyArm || ui.screen == Screen.MANUAL || ui.screen == Screen.ALIGN) && mine != null) return mine.first.toStage(local)
        val rp = rigPose ?: return local
        val q = ArmLayout.poseRotation(a.p.upright)
        return rp.position + Quat4.axisAngle(Vec3.UP, rp.yaw + spinBase).rotate(q.rotate(local - a.pivot()))
    }

    // ---- stick: turn the arm about its own vertical axis (T6, D1), dead zone 0.2

    private fun stepStick(ui: BwUiState, dt: Float, now: Long) {
        val (sx, _) = controllers.stick()
        if (abs(sx) < STICK_DEAD || dt <= 0f) return
        val rate = -sx * STICK_DEG_S * (abs(sx) - STICK_DEAD) / (1f - STICK_DEAD)
        turnArm(Math.toRadians((rate * dt).toDouble()).toFloat(), "stick")
        if (now - lastStickLog > 500) {
            lastStickLog = now
            BwLog.i(String.format(Locale.US, "STICK x=%.2f yaw=%.1f", sx, Math.toDegrees((rigPose?.yaw ?: 0f).toDouble())))
        }
    }

    /** Turns the table arm (or the ghost in manual align) about the vertical through its pivot / wrist. */
    fun turnArm(dYaw: Float, why: String) {
        val ui = BwController.state.value
        if (ui.screen == Screen.MANUAL) {
            val p = BwController.myArm?.first ?: return
            val q = Quat4.axisAngle(Vec3.UP, dYaw)
            BwController.onManualPose(ArmPose(p.wrist, (q * p.rotation).normalized()))
            return
        }
        if (ui.onMyArm) return
        val rp = rigPose ?: return
        rigPose = Upright(rp.position, Yaw.wrap(rp.yaw + dYaw))
        if (why != "stick") BwLog.i(String.format(Locale.US, "TURN-ARM why=%s yaw=%.1f", why, Math.toDegrees(rigPose!!.yaw.toDouble())))
    }

    // ---- 自动转一圈 (T7): 8 s ease-in-out, 360°, back to the same heading

    private fun stepSpin(now: Long) {
        if (BwController.spinEpoch != spinEpoch) {
            spinEpoch = BwController.spinEpoch
            spinStart = now
            BwLog.i("SPIN begin")
        }
        if (spinStart == 0L) return
        val t = ((now - spinStart) / SPIN_MS).coerceIn(0f, 1f)
        val e = (1 - cos(PI * t).toFloat()) / 2
        spinBase = (2 * PI).toFloat() * e
        if (t >= 1f) {
            spinStart = 0L
            spinBase = 0f
            BwLog.i(String.format(Locale.US, "SPIN end yaw=%.2f", Math.toDegrees((rigPose?.yaw ?: 0f).toDouble())))
        }
    }

    // ---- 放到我手臂上 (S04): track the arm's hand, lock after 1 s steady (T9); never on the emulator unless injected

    private fun stepAlign(ui: BwUiState, now: Long) {
        val aligning = ui.screen == Screen.ALIGN
        if (!aligning) {
            if (hands.running) hands.stop()
            steady.reset()
            supportAskedMs = 0L
            return
        }
        if (!hands.running && !Device.onEmulator && injected == null) {
            hands.start()
            supportAskedMs = now
        }
        val right = ui.prefs.side == ArmSide.RIGHT
        val hand: HandJoints? =
            injected?.takeIf { it.right == right }
                ?: if (hands.running) hands.sample().let { (l, r) -> if (right) r else l } else null
        val support = if (injected != null) "SUPPORTED" else hands.support()
        val unsupported = injected == null && hands.running && support != null && support != "SUPPORTED" && now - supportAskedMs > 1500
        BwController.onAlignFrame(handSeen = hand != null, unsupported = unsupported || hands.forcedSupport != null)
        val fit = hand?.let { Align.fromHand(it) }
        if (fit != null) BwController.onManualPose(fit.first) // the ghost previews where it will lock
        if (fit != null && steady.step(now, fit.first.wrist)) {
            steady.reset()
            hands.stop()
            BwLog.i("TRACK hand stop (locked)")
            BwController.onAlignLocked(fit.first, fit.second)
        } else if (fit == null) steady.reset()
    }

    /** S04b default ghost pose: the last own-arm pose, else where the table arm is now. */
    fun manualStartPose(): ArmPose? {
        BwController.myArm?.let { return it.first }
        val a = arm ?: return null
        val rp = rigPose ?: return null
        return ArmPose(stage(Vec3.ZERO), (Quat4.axisAngle(Vec3.UP, rp.yaw) * ArmLayout.poseRotation(a.p.upright)).normalized())
    }

    // ---- the bandage: laid turns, the growing one, the roll, the next-turn line, x-ray, ghost arm

    private fun stepBandage(ui: BwUiState) {
        val k = kit ?: return
        val a = arm ?: return
        val pb = BwController.playback
        val own = ui.onMyArm || ui.screen == Screen.MANUAL || ui.screen == Screen.ALIGN
        val ghost = own
        if (ghostShown != ghost) {
            ghostShown = ghost
            armEnt?.setMaterial(if (ghost) k.ghost else k.skin)
            BwLog.i("ARM look=${if (ghost) "ghost 18%" else "skin opaque"}")
        }
        val armVisible = !(ui.screen == Screen.ALIGN && BwController.myArm == null)
        armEnt?.on(armVisible)
        // manual-align handles (S04b)
        val manual = ui.screen == Screen.MANUAL
        wristHandle?.on(manual)
        elbowHandle?.on(manual)
        if (manual) {
            if (BwController.myArm == null) manualStartPose()?.let { BwController.onManualPose(it) }
            BwController.myArm?.first?.let { p ->
                wristHandle?.setLocal(position = p.wrist + p.rotation.rotate(Vec3(0f, 0.04f, 0f)))
                elbowHandle?.setLocal(position = p.toStage(Vec3(0f, 0.05f, -a.L)))
            }
        }
        val bandageOn = pb != null && builtWrap == ui.wrapId && (ui.screen == Screen.WATCH || ui.screen == Screen.DONE) && turns.isNotEmpty()
        if (!bandageOn) {
            for (i in turnEnts.indices) setTurn(i, false)
            current?.on(false)
            rollEnt?.on(false)
            arrowEnt?.on(false)
            nextLine?.on(false)
            hideLabel()
            return
        }
        pb!!
        val done = ui.screen == Screen.DONE || pb.finished
        val cur = if (done) turns.size else pb.turn
        if (shownXray != ui.xray) {
            shownXray = ui.xray
            turnEnts.forEach { it.setMaterial(if (ui.xray) k.xray else k.cream) }
            BwLog.i("XRAY ${if (ui.xray) "on" else "off"}")
        }
        for (i in turnEnts.indices) setTurn(i, i < cur)
        if (done) {
            current?.on(false)
            rollEnt?.on(false)
            arrowEnt?.on(false)
            nextLine?.on(false)
            showLabel(a, turns.last(), turns.last().samples - 1, ui, true)
            return
        }
        val t = turns[cur]
        val pos = pb.prog * (t.samples - 1)
        val rowsFull = pos.toInt() + 1
        val frac = pos - pos.toInt()
        val key = "$cur:$rowsFull:${(frac * 4).toInt()}"
        if (key != currentKey) {
            currentKey = key
            val cm = currentMesh
            if (cm != null) Meshes.strip(t, rowsFull, frac).replace(cm)
            current?.on(rowsFull >= 1)
        }
        // the roll at the leading edge, its axis across the bandage, riding on the surface; arrow ahead of it
        val i = rowsFull.coerceIn(0, t.samples - 1)
        val j = (i + 1).coerceAtMost(t.samples - 1)
        val c = t.centres[i].lerp(t.centres[j], frac)
        val n = t.centreNormals[i]
        val ac = t.across[i]
        val fwd = ac.cross(n).let { if (it.length < 1e-6f) Vec3(0f, 0f, 1f) else it.normalized() }
        rollEnt?.let { r ->
            r.on(true)
            r.setLocal(position = c + n * (ROLL_R + 0.0015f), rotation = Quat4.fromBasis(ac, n, ac.cross(n).normalized()))
        }
        arrowEnt?.let { ar ->
            ar.on(true)
            // arrow lies on the skin just ahead of the roll, pointing the way the bandage goes (−fwd is local −Z)
            val ahead = c + (-fwd) * (ROLL_R + 0.012f) + n * 0.004f
            ar.setLocal(position = ahead, rotation = Quat4.fromBasis(ac, n, fwd))
        }
        // next-turn line: where the next turn will lie (design nextTurnLine), breathing at 1 Hz via on/off duty
        val nx = cur + 1
        if (nx != nextLineFor) {
            nextLineFor = nx
            val lm = nextLineMesh
            if (nx < turns.size && lm != null) {
                val tn = turns[nx]
                val off = WrapBuilder.lift(tn.maxLayer, tn.index) + 0.0006f
                Meshes.dashes(WrapBuilder.centreLine(a, tn, off), tn.path.map { if (it.thumb) a.thumbNormal(it.b) else a.normal(it.a, it.b) }).replace(lm)
            }
        }
        val breathe = ((SystemClock.elapsedRealtime() / 500) % 2L) == 0L
        nextLine?.on(nx < turns.size && (breathe || !pb.playing))
        showLabel(a, t, i, ui, false)
    }

    private fun setTurn(i: Int, on: Boolean) {
        if (turnShown[i] == on) return
        turnShown[i] = on
        turnEnts[i].on(on)
    }

    // ---- the turn label: over the limb at this turn's u (θ = top), only along u, yaw-only facing (design turnLabel)

    private fun showLabel(a: ArmModel, t: TurnGeom, row: Int, ui: BwUiState, done: Boolean) {
        val slot = panels?.get(PanelIds.LABEL) ?: return
        val u = t.spineU[row.coerceIn(0, t.samples - 1)]
        val f = a.frame(u)
        val sec = a.section(u)
        val topLocal = f.c + f.y * (sec.ry + LABEL_LIFT + LABEL_H * LABEL_SCALE * 0.0008f / 2)
        val mirrored = if (a.p.side == ArmSide.LEFT) Vec3(-topLocal.x, topLocal.y, topLocal.z) else topLocal
        var at = stage(mirrored)
        // the label stays above the arm in Stage terms (upright pose: lift it up and towards you)
        if (a.p.upright && !ui.onMyArm) at = at + Vec3(0f, 0.03f, 0f)
        val zh = isZh(ui)
        val w = Wraps.byId(ui.wrapId ?: "") ?: return
        val top = if (done) (if (zh) "缠好了" else "Done") else "${ui.turn + 1} / ${turns.size}"
        val sub = if (done) w.scene.of(zh) else Wraps.copy(w, t.kind, t.index, turns.size).label.of(zh)
        if (labelTop.value != top) labelTop.value = top
        if (labelSub.value != sub) labelSub.value = sub
        if (slot.shown) slot.moveTo(at) else slot.show(at)
    }

    private fun hideLabel() {
        panels?.get(PanelIds.LABEL)?.hide()
    }

    private fun isZh(ui: BwUiState): Boolean {
        val ctx = appContext ?: return true
        return when (ui.prefs.lang) {
            io.embodit.bandagewrap.ui.Lang.ZH -> true
            io.embodit.bandagewrap.ui.Lang.EN -> false
            io.embodit.bandagewrap.ui.Lang.SYSTEM -> ctx.resources.configuration.locales[0].language == "zh"
        }
    }

    // ---- 3D input (board-input): drag the arm (T5), the manual-align handles (T10); taps do nothing on the arm

    fun startDrag(t: Target, src: String): Boolean {
        val ui = BwController.state.value
        when (t) {
            Target.ARM -> {
                if (ui.onMyArm || ui.screen == Screen.ALIGN || ui.screen == Screen.MANUAL) return false
                dragStart = rigPose ?: return false
            }
            Target.WRIST_HANDLE, Target.ELBOW_HANDLE -> {
                if (ui.screen != Screen.MANUAL) return false
                dragPoseStart = BwController.myArm?.first ?: return false
            }
        }
        dragTarget = t
        BwLog.i("DRAG start target=$t src=$src")
        return true
    }

    fun moveDrag(delta: Vec3) {
        when (dragTarget) {
            Target.ARM -> {
                val s = dragStart ?: return
                rigPose = Upright(s.position + delta, s.yaw)
            }
            Target.WRIST_HANDLE -> {
                val p = dragPoseStart ?: return
                BwController.onManualPose(ArmPose(p.wrist + delta, p.rotation))
            }
            Target.ELBOW_HANDLE -> {
                val p = dragPoseStart ?: return
                val a = arm ?: return
                val elbow0 = p.toStage(Vec3(0f, 0f, -a.L))
                val elbow = elbow0 + delta
                val newAxis = (elbow - p.wrist).normalized() // towards the elbow
                val q = Quat4.fromTo(p.towardElbow.normalized(), newAxis)
                BwController.onManualPose(ArmPose(p.wrist, (q * p.rotation).normalized()))
            }
            null -> Unit
        }
    }

    fun endDrag(src: String) {
        val t = dragTarget ?: return
        dragTarget = null
        if (t == Target.ARM) {
            val rp = rigPose ?: return
            val before = rp.position
            rigPose = ArmLayout.pushOut(rp, pose.position)
            BwLog.i(String.format(Locale.US, "DRAG end target=ARM src=%s rig=%s dist=%.2f pushed=%s", src, BwLog.pos(rigPose!!.position), ArmLayout.flatDist(rigPose!!.position, pose.position), before != rigPose!!.position))
        } else BwLog.i("DRAG end target=$t src=$src wrist=${BwLog.pos(BwController.myArm?.first?.wrist)}")
    }

    // ---- Stage panels: "打开面板" above the arm, the one-line hint on the line of sight

    private fun placePanels(ui: BwUiState, now: Long) {
        val pr = panels ?: return
        val hintOn = ui.loading || WindowCtl.levelHint || WindowCtl.recentering
        if (hintOn) {
            val pitchDeg = Math.toDegrees(pose.pitch.toDouble()).toFloat().coerceIn(-40f, 40f)
            val h = io.embodit.bandagewrap.domain.placeUpright(pose, 0f, 1.0f, -pitchDeg + 4f)
            pr[PanelIds.HINT]?.let { s -> if (s.shown) s.moveTo(h.position) else s.show(h.position) }
        } else pr[PanelIds.HINT]?.hide()
        val rp = rigPose
        if (rp != null && WindowCtl.chipWanted(now) && !ui.loading) {
            val at = rp.position + Vec3(0f, 0.20f, 0f)
            pr[PanelIds.CHIP]?.let { s -> if (s.shown) s.moveTo(at) else s.show(at); s.facePoint = pose.position }
        } else pr[PanelIds.CHIP]?.hide()
    }

    // =========================================================================================== logs / debug

    private fun stepLogs(ui: BwUiState, now: Long) {
        val since = now - startMs
        if (startupLogs < 3 && since > STARTUP_LOG_MS[startupLogs]) {
            startupLogs++
            BwLog.i("STARTUP t=${since / 1000}s ${where()} window=${WindowCtl.panelState} state=${ui.designState}")
        }
    }

    /** Head-relative placement of the arm (D1 1/3/5 s startup log, debug `dump arm`). */
    fun where(): String {
        val rp = rigPose ?: return "arm=none head=${BwLog.pos(pose.position)} hasHead=$hasHead"
        val a = arm ?: return "arm=unbuilt"
        val wrist = stage(Vec3.ZERO)
        val tips = stage(a.point(a.uMin, 0f))
        return String.format(Locale.US, "arm pivot=%s yaw=%.1f wrist=%s distWrist=%.2f distTips=%.2f bearing=%.1f° down=%.1f° visible=%s head=%s", BwLog.pos(rp.position), Math.toDegrees(rp.yaw.toDouble()), BwLog.pos(wrist), ArmLayout.flatDist(wrist, pose.position), ArmLayout.flatDist(tips, pose.position), ArmLayout.bearingDeg(pose, rp.position), ArmLayout.downDeg(pose.position, rp.position), rig?.enabled, BwLog.pos(pose.position))
    }

    fun dumpTurn(): String {
        val pb = BwController.playback ?: return "TURN none"
        val t = turns.getOrNull(pb.turn) ?: return "TURN none"
        val a = arm ?: return "TURN none"
        val mid = t.samples / 2
        return String.format(Locale.US, "TURN %d/%d kind=%s prog=%.2f width=%.3f maxLayer=%d mid=%s roll=%s", pb.turn + 1, turns.size, t.kind, pb.prog, t.width, t.maxLayer, BwLog.pos(stage(t.centres[mid])), BwLog.pos(stage(t.centres[(pb.prog * (t.samples - 1)).toInt()]))) + " arm=${a.p}"
    }

    fun headPose(): HeadPose = pose

    fun rigPoseOrNull(): Upright? = rigPose

    fun armModel(): ArmModel? = arm

    fun turnGeoms(): List<TurnGeom> = turns

    /** Debug `move dx dy dz`: the same path as a drag of the arm. */
    fun debugMove(dx: Float, dy: Float, dz: Float) {
        if (startDrag(Target.ARM, "debug")) {
            moveDrag(Vec3(dx, dy, dz))
            endDrag("debug")
        }
    }

    fun debugDrag(target: Target, dx: Float, dy: Float, dz: Float) {
        if (startDrag(target, "debug")) {
            moveDrag(Vec3(dx, dy, dz))
            endDrag("debug")
        }
    }

    fun debugSupport(state: String?) {
        hands.forcedSupport = state
    }

    companion object {
        const val SUN_LUX = 1800f
        const val NO_HEAD_MS = 5000L
        const val SPIN_MS = 8000f
        const val STICK_DEAD = 0.2f
        const val STICK_DEG_S = 90f
        const val ROLL_R = 0.022f
        const val PANEL_SCALE = 0.75f
        const val CHIP_W = 200f
        const val CHIP_H = 76f
        const val HINT_W = 560f
        const val HINT_H = 80f
        const val LABEL_W = 152f
        const val LABEL_H = 64f

        /** 0.53 mm/dp (design turnLabel) at the attachment's 0.8 mm/dp. */
        const val LABEL_SCALE = 0.00053f / 0.0008f

        /** Label centre 0.06 m over the skin (its bottom edge ~0.043 m over the skin). */
        const val LABEL_LIFT = 0.043f
        private val STARTUP_LOG_MS = longArrayOf(1000L, 3000L, 5000L)

        val RESOLVER: TargetResolver<Target> = TargetResolver.byName { Target.ofName(it) }
    }
}

/** Meshes and materials shared by all wraps (built off the main thread). */
internal class Kit(val sh: SharedResources) {
    private val m = SceneMaterials(sh)
    val skin: Material = m.pbr(BwColors.Skin, 0.72f)
    val ghost: Material = m.sheer(BwColors.Skin, 0.18f)
    val cream: Material = m.pbrTextured(m.texture(Meshes.crepeBitmap()), 0.9f)
    val xray: Material = m.sheer(BwColors.Gauze, 0.2f)
    val warm: Material = m.pbr(BwColors.CurrentTurn, 0.85f)
    val orange: Material = m.unlit(BwColors.Lead)
    val handle: Material = m.unlit(BwColors.Lead)
    val rollSide: Material = m.pbr(BwColors.Gauze, 0.9f)
    val rollEnd: Material = m.pbrTextured(m.texture(Meshes.rollBitmap()), 0.9f)
    val arrow: MeshResource = sh.keep(Meshes.arrow().build("arrow"))
    val ball: MeshResource = sh.keep(Meshes.ball(0.012f).build("ball"))

    @Suppress("unused")
    private val keepColor = Color.White
}
