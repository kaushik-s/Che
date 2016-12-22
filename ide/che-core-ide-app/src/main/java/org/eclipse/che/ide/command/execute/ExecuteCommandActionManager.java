/*******************************************************************************
 * Copyright (c) 2012-2016 Codenvy, S.A.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *   Codenvy, S.A. - initial API and implementation
 *******************************************************************************/

package org.eclipse.che.ide.command.execute;

import com.google.gwt.core.client.Callback;
import com.google.inject.Inject;
import com.google.inject.Singleton;

import org.eclipse.che.ide.api.action.Action;
import org.eclipse.che.ide.api.action.ActionManager;
import org.eclipse.che.ide.api.action.DefaultActionGroup;
import org.eclipse.che.ide.api.command.CommandManager;
import org.eclipse.che.ide.api.command.CommandManager.CommandChangedListener;
import org.eclipse.che.ide.api.command.CommandManager.CommandLoadedListener;
import org.eclipse.che.ide.api.command.ContextualCommand;
import org.eclipse.che.ide.api.command.PredefinedCommandGoalRegistry;
import org.eclipse.che.ide.api.component.Component;

import java.util.HashMap;
import java.util.Map;

import static com.google.common.base.Strings.isNullOrEmpty;
import static org.eclipse.che.api.workspace.shared.Constants.COMMAND_GOAL_ATTRIBUTE_NAME;
import static org.eclipse.che.ide.api.action.IdeActions.GROUP_CONSOLES_TREE_CONTEXT_MENU;
import static org.eclipse.che.ide.api.action.IdeActions.GROUP_EDITOR_TAB_CONTEXT_MENU;
import static org.eclipse.che.ide.api.action.IdeActions.GROUP_MAIN_CONTEXT_MENU;

/**
 * Manager listens for creating/removing commands and adds/removes
 * related {@link ExecuteCommandAction}s in the context menus.
 *
 * @author Artem Zatsarynnyi
 */
@Singleton
public class ExecuteCommandActionManager implements Component,
                                                    CommandLoadedListener,
                                                    CommandChangedListener {

    private final CommandManager                commandManager;
    private final ActionManager                 actionManager;
    private final CommandsActionGroup           commandsActionGroup;
    private final GoalPopUpGroupFactory         goalPopUpGroupFactory;
    private final ExecuteCommandActionFactory   executeCommandActionFactory;
    private final PredefinedCommandGoalRegistry predefinedCommandGoalRegistry;

    private final Map<String, Action>             command2Action;
    private final Map<String, DefaultActionGroup> commandGoalPopUpGroups;

    @Inject
    public ExecuteCommandActionManager(CommandManager commandManager,
                                       ActionManager actionManager,
                                       CommandsActionGroup commandsActionGroup,
                                       GoalPopUpGroupFactory goalPopUpGroupFactory,
                                       ExecuteCommandActionFactory executeCommandActionFactory,
                                       PredefinedCommandGoalRegistry predefinedCommandGoalRegistry) {
        this.commandManager = commandManager;
        this.actionManager = actionManager;
        this.commandsActionGroup = commandsActionGroup;
        this.goalPopUpGroupFactory = goalPopUpGroupFactory;
        this.executeCommandActionFactory = executeCommandActionFactory;
        this.predefinedCommandGoalRegistry = predefinedCommandGoalRegistry;

        command2Action = new HashMap<>();
        commandGoalPopUpGroups = new HashMap<>();
    }

    @Override
    public void start(Callback<Component, Exception> callback) {
        callback.onSuccess(this);

        commandManager.addCommandLoadedListener(this);
        commandManager.addCommandChangedListener(this);

        actionManager.registerAction("commandsActionGroup", commandsActionGroup);

        // inject 'Commands' menu into context menus
        ((DefaultActionGroup)actionManager.getAction(GROUP_MAIN_CONTEXT_MENU)).add(commandsActionGroup);
        ((DefaultActionGroup)actionManager.getAction(GROUP_EDITOR_TAB_CONTEXT_MENU)).add(commandsActionGroup);
        ((DefaultActionGroup)actionManager.getAction(GROUP_CONSOLES_TREE_CONTEXT_MENU)).add(commandsActionGroup);
    }

    @Override
    public void onCommandsLoaded() {
        for (ContextualCommand command : commandManager.getCommands()) {
            addAction(command);
        }
    }

    @Override
    public void onCommandAdded(ContextualCommand command) {
        addAction(command);
    }

    /**
     * Creates action for executing the given command and
     * adds created action to the appropriate action group.
     */
    private void addAction(ContextualCommand command) {
        final ExecuteCommandAction action = executeCommandActionFactory.create(command);

        actionManager.registerAction("command_" + command.getName(), action);
        command2Action.put(command.getName(), action);

        getActionGroupForCommand(command).add(action);
    }

    /**
     * Returns the action group which is appropriate for placing the action for executing the given command.
     * If appropriate action group doesn't exist it will be created and added to the right place.
     */
    private DefaultActionGroup getActionGroupForCommand(ContextualCommand command) {
        String goalId = command.getAttributes().get(COMMAND_GOAL_ATTRIBUTE_NAME);
        if (isNullOrEmpty(goalId)) {
            goalId = predefinedCommandGoalRegistry.getDefaultGoal().getId();
        }

        DefaultActionGroup commandGoalPopUpGroup = commandGoalPopUpGroups.get(goalId);

        if (commandGoalPopUpGroup == null) {
            commandGoalPopUpGroup = goalPopUpGroupFactory.create(goalId);

            actionManager.registerAction("goal_" + goalId, commandGoalPopUpGroup);
            commandGoalPopUpGroups.put(goalId, commandGoalPopUpGroup);

            commandsActionGroup.add(commandGoalPopUpGroup);
        }

        return commandGoalPopUpGroup;
    }

    @Override
    public void onCommandUpdated(ContextualCommand command) {
        // TODO: update/replace action
    }

    @Override
    public void onCommandRemoved(ContextualCommand command) {
        removeAction(command);
    }

    /**
     * Removes action for executing the given command and
     * removes the appropriate action group in case it's empty.
     */
    private void removeAction(ContextualCommand command) {
        final Action action = command2Action.remove(command.getName());

        if (action != null) {
            final String actionId = actionManager.getId(action);

            if (actionId != null) {
                actionManager.unregisterAction(actionId);
            }

            // remove action from it's action group
            final DefaultActionGroup commandTypePopUpGroup = commandGoalPopUpGroups.get(command.getType());

            if (commandTypePopUpGroup != null) {
                commandTypePopUpGroup.remove(action);

                // remove action group if it is empty
                if (commandTypePopUpGroup.getChildrenCount() == 0) {
                    commandsActionGroup.remove(commandTypePopUpGroup);
                }
            }
        }
    }
}
