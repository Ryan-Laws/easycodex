package com.easycodex.mobile

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import java.util.Locale

private data class DrawerAgentItem(
    val id: String,
    val name: String,
    val projectPath: String,
    val status: String,
    val activity: String?,
    val updatedAt: Long,
) {
    fun isBusy(): Boolean {
        return status.trim().lowercase(Locale.ROOT) in setOf(
            "initializing",
            "resuming",
            "working",
            "running",
            "active",
            "in_progress",
            "inprogress",
            "in-progress",
            "pending",
            "processing",
            "queued",
            "starting",
            "streaming",
        )
    }
}

@Composable
fun AgentDrawer(
    agents: List<Agent>,
    activeAgentId: String?,
    onHome: () -> Unit,
    onSelect: (String) -> Unit,
    onCreateInProject: (String) -> Unit,
) {
    val strings = LocalAppStrings.current
    var query by remember { mutableStateOf("") }
    var debouncedQuery by remember { mutableStateOf("") }
    LaunchedEffect(query) {
        delay(180)
        debouncedQuery = query
    }
    val normalizedQuery = debouncedQuery.trim().lowercase(Locale.ROOT)
    val drawerAgents by remember {
        derivedStateOf {
            agents.map { agent ->
                DrawerAgentItem(
                    id = agent.id,
                    name = agent.name,
                    projectPath = cleanNullablePath(agent.projectRoot) ?: cleanNullablePath(agent.cwd) ?: DEFAULT_AGENT_CWD,
                    status = agent.status,
                    activity = agent.activity,
                    updatedAt = agent.updatedAt,
                )
            }
        }
    }
    val visibleAgents = remember(drawerAgents, normalizedQuery) {
        if (normalizedQuery.isBlank()) {
            drawerAgents
        } else {
            drawerAgents.filter { agent ->
                listOf(agent.name, agent.projectPath, agent.status, agent.activity.orEmpty())
                    .any { it.lowercase(Locale.ROOT).contains(normalizedQuery) }
            }
        }
    }
    val groupedAgents = remember(visibleAgents) {
        visibleAgents
            .sortedByDescending { it.updatedAt }
            .groupBy { it.projectPath }
    }
    val projectPaths = groupedAgents.keys.toList()
    var collapsedProjectPaths by remember { mutableStateOf(emptySet<String>()) }
    val allProjectsCollapsed = projectPaths.isNotEmpty() && projectPaths.all { it in collapsedProjectPaths }

    LaunchedEffect(projectPaths) {
        collapsedProjectPaths = collapsedProjectPaths.intersect(projectPaths.toSet())
    }

    ModalDrawerSheet(
        drawerContainerColor = MaterialTheme.colorScheme.background,
        drawerContentColor = MaterialTheme.colorScheme.onBackground,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 22.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            EasyCodexAppIcon(
                modifier = Modifier.size(40.dp),
                contentDescription = "EasyCodex",
            )
            Spacer(Modifier.width(12.dp))
            Column {
                Text("EasyCodex", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(strings.easyCodexAgents, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Surface(
            color = if (activeAgentId == null) MaterialTheme.colorScheme.surfaceContainer else Color.Transparent,
            shape = RoundedCornerShape(18.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp)
                .clip(RoundedCornerShape(18.dp))
                .clickable(onClick = onHome),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Default.Home,
                    contentDescription = null,
                    modifier = Modifier.size(22.dp),
                    tint = if (activeAgentId == null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    strings.home,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            singleLine = true,
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            placeholder = { Text(strings.searchTasksOrProjects) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 6.dp),
        )
        if (agents.isEmpty()) {
            Text(
                strings.noAgents,
                modifier = Modifier.padding(24.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@ModalDrawerSheet
        }
        if (visibleAgents.isEmpty()) {
            Text(
                strings.noMatchingTasks,
                modifier = Modifier.padding(24.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@ModalDrawerSheet
        }
        LazyColumn(
            modifier = Modifier
                .weight(1f),
            contentPadding = PaddingValues(bottom = 12.dp),
        ) {
            item("drawer_projects_header") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 18.dp, end = 12.dp, top = 18.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        strings.projects,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    TextButton(
                        onClick = {
                            collapsedProjectPaths = if (allProjectsCollapsed) {
                                emptySet()
                            } else {
                                projectPaths.toSet()
                            }
                        },
                    ) {
                        Text(if (allProjectsCollapsed) strings.expandAll else strings.collapseAll)
                    }
                }
            }
            groupedAgents.forEach { (projectPath, projectAgents) ->
                val expanded = projectPath !in collapsedProjectPaths
                item("project_$projectPath") {
                    ProjectHeader(
                        name = projectNameFromCwd(projectPath),
                        taskCount = projectAgents.size,
                        expanded = expanded,
                        onToggle = {
                            collapsedProjectPaths = if (expanded) {
                                collapsedProjectPaths + projectPath
                            } else {
                                collapsedProjectPaths - projectPath
                            }
                        },
                        onCreate = { onCreateInProject(projectPath) },
                    )
                }
                item("project_agents_$projectPath") {
                    AnimatedVisibility(
                        visible = expanded,
                        enter = expandVertically(
                            animationSpec = tween(durationMillis = 260, easing = FastOutSlowInEasing),
                            expandFrom = Alignment.Top,
                        ) + fadeIn(animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing)),
                        exit = shrinkVertically(
                            animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing),
                            shrinkTowards = Alignment.Top,
                        ) + fadeOut(animationSpec = tween(durationMillis = 140, easing = FastOutSlowInEasing)),
                    ) {
                        Column(
                            modifier = Modifier.animateContentSize(
                                animationSpec = tween(durationMillis = 240, easing = FastOutSlowInEasing),
                            ),
                        ) {
                            projectAgents.forEach { agent ->
                                DrawerAgentProjectRow(
                                    agent = agent,
                                    selected = agent.id == activeAgentId,
                                    onClick = { onSelect(agent.id) },
                                )
                            }
                        }
                    }
                }
                item("project_spacer_$projectPath") {
                    Spacer(Modifier.height(8.dp))
                }
            }
        }
    }
}

@Composable
private fun DrawerAgentProjectRow(agent: DrawerAgentItem, selected: Boolean, onClick: () -> Unit) {
    val strings = LocalAppStrings.current
    val rowColor = if (selected) MaterialTheme.colorScheme.surfaceContainer else Color.Transparent
    Surface(
        color = rowColor,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 28.dp, end = 12.dp, top = 2.dp, bottom = 2.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                shape = CircleShape,
                color = when {
                    agent.isBusy() -> MaterialTheme.colorScheme.primary
                    agent.status.equals("error", ignoreCase = true) -> MaterialTheme.colorScheme.error
                    else -> MaterialTheme.colorScheme.outlineVariant
                },
                modifier = Modifier.size(8.dp),
            ) {}
            Spacer(Modifier.width(10.dp))
            Text(
                agent.name,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.width(8.dp))
            if (agent.isBusy()) {
                Row(
                    modifier = Modifier.weight(0.72f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.End,
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(15.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        agent.activity?.takeIf { it.isNotBlank() } ?: "加载中",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            } else {
                Text(
                    relativeTime(agent.updatedAt, strings),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
fun ProjectHeader(
    name: String,
    taskCount: Int,
    expanded: Boolean,
    onToggle: () -> Unit,
    onCreate: () -> Unit,
) {
    val strings = LocalAppStrings.current
    val arrowRotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing),
        label = "project header arrow rotation",
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing))
            .padding(start = 16.dp, end = 12.dp, top = 8.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Default.Folder,
            contentDescription = null,
            modifier = Modifier.size(22.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                name,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                strings.tasks(taskCount),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
        Spacer(Modifier.width(8.dp))
        IconButton(
            onClick = onToggle,
            modifier = Modifier.size(36.dp),
        ) {
            Icon(
                Icons.Default.KeyboardArrowDown,
                contentDescription = if (expanded) strings.projectTaskCollapse else strings.projectTaskExpand,
                modifier = Modifier
                    .size(22.dp)
                    .graphicsLayer(rotationZ = arrowRotation),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(
            onClick = onCreate,
            modifier = Modifier.size(36.dp),
        ) {
            Icon(
                Icons.Default.Add,
                contentDescription = strings.createInProject,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
fun AgentProjectRow(agent: Agent, selected: Boolean, onClick: () -> Unit) {
    val strings = LocalAppStrings.current
    val rowColor = if (selected) MaterialTheme.colorScheme.surfaceContainer else Color.Transparent
    Surface(
        color = rowColor,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 28.dp, end = 12.dp, top = 2.dp, bottom = 2.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                shape = CircleShape,
                color = when {
                    agent.isBusy() -> MaterialTheme.colorScheme.primary
                    agent.status.equals("error", ignoreCase = true) -> MaterialTheme.colorScheme.error
                    else -> MaterialTheme.colorScheme.outlineVariant
                },
                modifier = Modifier.size(8.dp),
            ) {}
            Spacer(Modifier.width(10.dp))
            Text(
                agent.name,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.width(8.dp))
            if (agent.isBusy()) {
                Row(
                    modifier = Modifier.weight(0.72f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.End,
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(15.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        agent.activity?.takeIf { it.isNotBlank() } ?: "加载中",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            } else {
                Text(
                    relativeTime(agent.updatedAt, strings),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HomeTaskScreen(
    draftAgent: Agent,
    projectOptions: List<String>,
    reasoningOptions: List<String>,
    serviceTierOptions: List<String>,
    recentAgents: List<Agent>,
    runtimeCapabilities: RuntimeCapabilities,
    canChangeProject: Boolean,
    onProjectChange: (String) -> Unit,
    onReasoningEffortChange: (String) -> Unit,
    onServiceTierChange: (String) -> Unit,
    onOpenAgent: (String) -> Unit,
    onBrowseDirectories: (String?, (DirectoryListing?, String?) -> Unit) -> Unit,
) {
    val strings = LocalAppStrings.current
    var showProjectPicker by remember { mutableStateOf(false) }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 18.dp, bottom = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(9.dp),
            ) {
                EasyCodexAppIcon(modifier = Modifier.size(50.dp), contentDescription = "EasyCodex")
                Text(
                    strings.homeQuestion,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                )
                Text(
                    strings.sendToEasyCodex,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }
        if (canChangeProject) item {
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)),
                tonalElevation = 1.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(22.dp))
                    .clickable { showProjectPicker = true },
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 15.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        EasyCodexIconBubble(icon = Icons.Default.Folder, modifier = Modifier.size(42.dp))
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                strings.project,
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                projectNameFromCwd(draftAgent.cwd),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Surface(
                            shape = RoundedCornerShape(999.dp),
                            color = MaterialTheme.colorScheme.surface,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
                        ) {
                            Text(
                                strings.chooseProjectDirectory,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                            )
                        }
                    }
                    Text(
                        draftAgent.cwd,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        val recent = recentAgents.sortedByDescending { it.updatedAt }.take(5)
        if (recent.isNotEmpty()) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        strings.recentTasks,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            items(recent, key = { it.id }) { agent ->
                AgentProjectRow(
                    agent = agent,
                    selected = false,
                    onClick = { onOpenAgent(agent.id) },
                )
            }
        }
    }

    if (showProjectPicker && canChangeProject) {
        DirectoryPickerDialog(
            initialPath = draftAgent.cwd,
            pinnedPaths = projectOptions,
            onBrowseDirectories = onBrowseDirectories,
            onSelect = {
                onProjectChange(it)
                showProjectPicker = false
            },
            onDismiss = { showProjectPicker = false },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DirectoryPickerDialog(
    initialPath: String,
    pinnedPaths: List<String>,
    onBrowseDirectories: (String?, (DirectoryListing?, String?) -> Unit) -> Unit,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val strings = LocalAppStrings.current
    var listing by remember { mutableStateOf<DirectoryListing?>(null) }
    var currentPath by remember(initialPath) { mutableStateOf(initialPath) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    fun load(path: String?) {
        loading = true
        error = null
        onBrowseDirectories(path) { next, nextError ->
            loading = false
            if (next != null) {
                listing = next
                currentPath = next.path
            } else {
                error = nextError ?: strings.directoryUnreadable
            }
        }
    }

    LaunchedEffect(initialPath) {
        load(initialPath)
    }

    val pinned = pinnedPaths
        .mapNotNull { cleanNullablePath(it) }
        .distinctBy { normalizePathKey(it) }
        .filter { normalizePathKey(it) != normalizePathKey(currentPath) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(strings.chooseProjectDirectory) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    pinned.take(4).forEach { path ->
                        AssistChip(
                            onClick = { load(path) },
                            label = { Text(projectNameFromCwd(path), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        )
                    }
                    listing?.roots.orEmpty().forEach { root ->
                        AssistChip(
                            onClick = { load(root.path) },
                            label = { Text(root.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        )
                    }
                }
                Text(
                    currentPath,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                val worktrees = listing?.worktrees.orEmpty()
                if (worktrees.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            "Git worktrees",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        worktrees.forEach { worktree ->
                            WorktreePickerRow(
                                worktree = worktree,
                                onClick = { load(worktree.path) },
                            )
                        }
                    }
                }
                if (loading) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        Text(strings.readingDirectory, style = MaterialTheme.typography.bodySmall)
                    }
                }
                error?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                LazyColumn(
                    modifier = Modifier.height(320.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    listing?.parent?.let { parent ->
                        item {
                            DirectoryPickerRow(
                                name = "..",
                                path = parent,
                                onClick = { load(parent) },
                            )
                        }
                    }
                    items(listing?.entries.orEmpty()) { entry ->
                        DirectoryPickerRow(
                            name = entry.name,
                            path = entry.path,
                            onClick = { load(entry.path) },
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSelect(listing?.path ?: currentPath) },
                enabled = listing != null,
            ) {
                Text(strings.useThisDirectory)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(strings.cancel) } },
    )
}

@Composable
private fun WorktreePickerRow(
    worktree: WorktreeOption,
    onClick: () -> Unit,
) {
    val detail = listOfNotNull(
        worktree.branch?.takeIf { it.isNotBlank() },
        if (worktree.current) "current" else null,
        if (worktree.locked) "locked" else null,
    ).joinToString(" · ")
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = if (worktree.current) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Default.TaskAlt,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = if (worktree.current) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(worktree.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (detail.isNotBlank()) {
                    Text(
                        detail,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    worktree.path,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun DirectoryPickerRow(
    name: String,
    path: String,
    onClick: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Default.Folder,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    path,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

