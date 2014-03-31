/*
 * IdeaVim - Vim emulator for IDEs based on the IntelliJ platform
 * Copyright (C) 2003-2013 The IdeaVim authors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */

package com.maddyhome.idea.vim.command;

import com.intellij.openapi.editor.Editor;
import com.maddyhome.idea.vim.VimPlugin;
import com.maddyhome.idea.vim.group.RegisterGroup;
import com.maddyhome.idea.vim.helper.EditorData;
import com.maddyhome.idea.vim.key.ParentNode;
import com.maddyhome.idea.vim.option.Options;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Stack;

public class CommandState {
  @Nullable private static Command lastChange = null;
  private static char lastRegister = RegisterGroup.REGISTER_DEFAULT;

  @NotNull private Stack<State> modes = new Stack<State>();
  @NotNull private State defaultState = new State(Mode.COMMAND, SubMode.NONE, MappingMode.NORMAL);
  @Nullable private Command command;
  private int flags;
  private boolean isRecording = false;

  private ParentNode currentNode = VimPlugin.getKey().getKeyRoot(getMappingMode());

  private CommandState() {
    modes.push(new State(Mode.COMMAND, SubMode.NONE, MappingMode.NORMAL));
  }

  @NotNull
  public static CommandState getInstance(@Nullable Editor editor) {
    if (editor == null) {
      return new CommandState();
    }

    CommandState res = EditorData.getCommandState(editor);
    if (res == null) {
      res = new CommandState();
      EditorData.setCommandState(editor, res);
    }

    return res;
  }

  public static boolean inInsertMode(@Nullable Editor editor) {
    final Mode mode = getInstance(editor).getMode();
    return mode == Mode.INSERT || mode == Mode.REPLACE;
  }

  public static boolean inRepeatMode(@Nullable Editor editor) {
    final Mode mode = getInstance(editor).getMode();
    return mode == Mode.REPEAT;
  }

  public static boolean inVisualCharacterMode(@Nullable Editor editor) {
    final CommandState state = getInstance(editor);
    return state.getMode() == Mode.VISUAL && state.getSubMode() == SubMode.VISUAL_CHARACTER;
  }

  @Nullable
  public Command getCommand() {
    return command;
  }

  public void setCommand(@NotNull Command cmd) {
    command = cmd;
    setFlags(cmd.getFlags());
  }

  public int getFlags() {
    return flags;
  }

  public void setFlags(int flags) {
    this.flags = flags;
  }

  public void pushState(@NotNull Mode mode, @NotNull SubMode submode, @NotNull MappingMode mappingMode) {
    modes.push(new State(mode, submode, mappingMode));
    updateStatus();
  }

  public void popState() {
    modes.pop();
    updateStatus();
  }

  @NotNull
  public Mode getMode() {
    return currentState().getMode();
  }

  @NotNull
  public SubMode getSubMode() {
    return currentState().getSubmode();
  }

  public void setSubMode(@NotNull SubMode submode) {
    currentState().setSubmode(submode);
    updateStatus();
  }

  @NotNull
  private String getStatusString(int pos) {
    State state;
    if (pos >= 0 && pos < modes.size()) {
      state = modes.get(pos);
    }
    else if (pos < 0) {
      state = defaultState;
    }
    else {
      return "";
    }

    final StringBuilder msg = new StringBuilder();
    switch (state.getMode()) {
      case COMMAND:
        if (state.getSubmode() == SubMode.SINGLE_COMMAND) {
          msg.append('(').append(getStatusString(pos - 1).toLowerCase()).append(')');
        }
        break;
      case INSERT:
        msg.append("INSERT");
        break;
      case REPLACE:
        msg.append("REPLACE");
        break;
      case VISUAL:
        if (pos > 0) {
          State tmp = modes.get(pos - 1);
          if (tmp.getMode() == Mode.COMMAND && tmp.getSubmode() == SubMode.SINGLE_COMMAND) {
            msg.append(getStatusString(pos - 1));
            msg.append(" - ");
          }
        }
        switch (state.getSubmode()) {
          case VISUAL_LINE:
            msg.append("VISUAL LINE");
            break;
          case VISUAL_BLOCK:
            msg.append("VISUAL BLOCK");
            break;
          default:
            msg.append("VISUAL");
        }
        break;
    }

    return msg.toString();
  }

  /**
   * Toggles the insert/overwrite state. If currently insert, goto replace mode. If currently replace, goto insert
   * mode.
   */
  public void toggleInsertOverwrite() {
    Mode oldmode = getMode();
    Mode newmode = oldmode;
    if (oldmode == Mode.INSERT) {
      newmode = Mode.REPLACE;
    }
    else if (oldmode == Mode.REPLACE) {
      newmode = Mode.INSERT;
    }

    if (oldmode != newmode) {
      State state = currentState();
      popState();
      pushState(newmode, state.getSubmode(), state.getMappingMode());
    }
  }

  /**
   * Resets the command, mode, visual mode, and mapping mode to initial values.
   */
  public void reset() {
    command = null;
    modes.clear();
    updateStatus();
  }

  /**
   * Gets the current key mapping mode
   *
   * @return The current key mapping mode
   */
  @NotNull
  public MappingMode getMappingMode() {
    return currentState().getMappingMode();
  }

  /**
   * Gets the last command that performed a change
   *
   * @return The last change command, null if there hasn't been a change yet
   */
  @Nullable
  public Command getLastChangeCommand() {
    return lastChange;
  }

  /**
   * Gets the register used by the last saved change command
   *
   * @return The register key
   */
  public char getLastChangeRegister() {
    return lastRegister;
  }

  /**
   * Saves the last command that performed a change. It also preserves the register the command worked with.
   *
   * @param cmd The change command
   */
  public void saveLastChangeCommand(Command cmd) {
    lastChange = cmd;
    lastRegister = VimPlugin.getRegister().getCurrentRegister();
  }

  public boolean isRecording() {
    return isRecording;
  }

  public void setRecording(boolean val) {
    isRecording = val;
    updateStatus();
  }

  public ParentNode getCurrentNode() {
    return currentNode;
  }

  public void setCurrentNode(ParentNode currentNode) {
    this.currentNode = currentNode;
  }

  private State currentState() {
    if (modes.size() > 0) {
      return modes.peek();
    }
    else {
      return defaultState;
    }
  }

  private void updateStatus() {
    final StringBuilder msg = new StringBuilder();
    if (Options.getInstance().isSet("showmode")) {
      msg.append(getStatusString(modes.size() - 1));
    }

    if (isRecording()) {
      if (msg.length() > 0) {
        msg.append(" - ");
      }
      msg.append("recording");
    }

    VimPlugin.showMode(msg.toString());
  }

  public static enum Mode {
    COMMAND,
    INSERT,
    REPLACE,
    REPEAT,
    VISUAL,
    EX_ENTRY

  }

  public static enum SubMode {
    NONE,
    SINGLE_COMMAND,
    VISUAL_CHARACTER,
    VISUAL_LINE,
    VISUAL_BLOCK
  }

  private class State {
    @NotNull private Mode mode;
    @NotNull private SubMode submode;
    @NotNull private MappingMode myMappingMode;

    public State(@NotNull Mode mode, @NotNull SubMode submode, @NotNull MappingMode mappingMode) {
      this.mode = mode;
      this.submode = submode;
      this.myMappingMode = mappingMode;
    }

    @NotNull
    public Mode getMode() {
      return mode;
    }

    @NotNull
    public SubMode getSubmode() {
      return submode;
    }

    public void setSubmode(@NotNull SubMode submode) {
      this.submode = submode;
    }

    @NotNull
    public MappingMode getMappingMode() {
      return myMappingMode;
    }
  }
}

