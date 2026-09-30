/* ###
 * IP: GHIDRA
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package ghidra.app.plugin.core.debug.gui.decompiler;

import java.awt.Color;
import java.util.Set;

import ghidra.app.decompiler.*;
import ghidra.app.plugin.PluginCategoryNames;
import ghidra.app.plugin.core.debug.DebuggerPluginPackage;
import ghidra.app.plugin.core.debug.event.TraceActivatedPluginEvent;
import ghidra.app.plugin.core.debug.gui.DebuggerResources;
import ghidra.app.plugin.core.debug.gui.action.DebuggerTrackLocationTrait;
import ghidra.app.services.DebuggerStaticMappingService;
import ghidra.app.services.DebuggerTraceManagerService;
import ghidra.debug.api.modules.DebuggerStaticMappingChangeListener;
import ghidra.framework.plugintool.*;
import ghidra.framework.plugintool.annotation.AutoServiceConsumed;
import ghidra.framework.plugintool.util.PluginStatus;
import ghidra.program.model.address.Address;
import ghidra.program.model.listing.Program;
import ghidra.program.util.ProgramLocation;
import ghidra.trace.model.Trace;

/**
 * Marks the current program counter in the Decompiler.
 * 
 * <p>
 * The listings mark the tracked PC, but the Decompiler only moves its caret to the start of the
 * PC's line, and that caret is also the user's. This plugin tracks the PC the same way the listings
 * do ({@link DebuggerTrackLocationTrait} with its default, PC, spec), maps it into the static
 * program, and then marks it independently of the caret: the tokens of the PC's instruction get the
 * tracked-register background, and their lines get the tracked-register marker in a Decompiler
 * margin, as {@code BreakpointsDecompilerMarginProvider} does for breakpoints.
 */
@PluginInfo(
	shortDescription = "Debugger program counter in the Decompiler",
	description = "Marks the current program counter in the Decompiler, independent of the caret",
	category = PluginCategoryNames.DEBUGGER,
	packageName = DebuggerPluginPackage.NAME,
	status = PluginStatus.RELEASED,
	eventsConsumed = {
		TraceActivatedPluginEvent.class,
	},
	servicesRequired = {
		DebuggerStaticMappingService.class,
	})
public class DebuggerDecompilerPcMarkerPlugin extends Plugin {
	static final String HIGHLIGHTER_ID = DebuggerDecompilerPcMarkerPlugin.class.getName();
	static final Color COLOR_PC = DebuggerResources.COLOR_REGISTER_MARKERS;

	private class PcTracker extends DebuggerTrackLocationTrait {
		PcTracker() {
			super(DebuggerDecompilerPcMarkerPlugin.this.getTool(),
				DebuggerDecompilerPcMarkerPlugin.this, null);
		}

		@Override
		protected void locationTracked() {
			updateStaticPc();
		}
	}

	private class PcMatcher implements CTokenHighlightMatcher {
		@Override
		public Color getTokenHighlight(ClangToken token) {
			return isPcToken(token) ? COLOR_PC : null;
		}
	}

	private final DebuggerStaticMappingChangeListener mappingsListener = this::mappingsChanged;
	private final PcTracker tracker;
	private final PcDecompilerMarginProvider marginProvider;

	@AutoServiceConsumed
	private DebuggerTraceManagerService traceManager;
	// @AutoServiceConsumed via method
	private DebuggerStaticMappingService mappingService;
	// @AutoServiceConsumed via method
	private DecompilerMarginService marginService;
	// @AutoServiceConsumed via method
	private DecompilerHighlightService highlightService;
	@SuppressWarnings("unused")
	private final AutoService.Wiring autoServiceWiring;

	private DecompilerHighlighter highlighter;
	/** The PC mapped into the static program, or null */
	private ProgramLocation staticPc;

	public DebuggerDecompilerPcMarkerPlugin(PluginTool tool) {
		super(tool);
		this.tracker = new PcTracker();
		this.marginProvider = new PcDecompilerMarginProvider(this);
		this.autoServiceWiring = AutoService.wireServicesConsumed(this, this);
	}

	@Override
	protected void init() {
		super.init();
		if (traceManager != null) {
			tracker.goToCoordinates(traceManager.getCurrent());
		}
	}

	@Override
	protected void dispose() {
		setMarginService(null);
		setHighlightService(null);
		setMappingService(null);
		super.dispose();
	}

	@Override
	public void processEvent(PluginEvent event) {
		super.processEvent(event);
		if (event instanceof TraceActivatedPluginEvent ev) {
			tracker.goToCoordinates(ev.getActiveCoordinates());
		}
	}

	@AutoServiceConsumed
	private void setMappingService(DebuggerStaticMappingService mappingService) {
		if (this.mappingService != null) {
			this.mappingService.removeChangeListener(mappingsListener);
		}
		this.mappingService = mappingService;
		if (this.mappingService != null) {
			this.mappingService.addChangeListener(mappingsListener);
		}
		updateStaticPc();
	}

	@AutoServiceConsumed
	private void setMarginService(DecompilerMarginService marginService) {
		if (this.marginService != null) {
			this.marginService.removeMarginProvider(marginProvider);
		}
		this.marginService = marginService;
		if (this.marginService != null) {
			this.marginService.addMarginProvider(marginProvider);
		}
	}

	@AutoServiceConsumed
	private void setHighlightService(DecompilerHighlightService highlightService) {
		if (highlighter != null) {
			highlighter.dispose();
			highlighter = null;
		}
		this.highlightService = highlightService;
		if (this.highlightService != null) {
			highlighter = this.highlightService.createHighlighter(HIGHLIGHTER_ID, new PcMatcher());
			refreshMarks();
		}
	}

	private void mappingsChanged(Set<Trace> affectedTraces, Set<Program> affectedPrograms) {
		updateStaticPc();
	}

	private void updateStaticPc() {
		ProgramLocation dynamic = tracker.getTrackedLocation();
		staticPc = dynamic == null || mappingService == null ? null
				: mappingService.getStaticLocationFromDynamic(dynamic);
		refreshMarks();
	}

	private void refreshMarks() {
		if (highlighter != null) {
			highlighter.clearHighlights();
			if (staticPc != null) {
				highlighter.applyHighlights();
			}
		}
		marginProvider.repaint();
	}

	DecompilerMarginService getMarginService() {
		return marginService;
	}

	/**
	 * Check if the given token is part of the instruction at the PC
	 * 
	 * @param token the token
	 * @return true if the token's address range covers the PC in the static program
	 */
	boolean isPcToken(ClangToken token) {
		ProgramLocation pc = staticPc;
		if (pc == null || token == null) {
			return false;
		}
		Address min = token.getMinAddress();
		Address max = token.getMaxAddress();
		if (min == null || max == null) {
			return false;
		}
		Address addr = pc.getAddress();
		if (!addr.getAddressSpace().equals(min.getAddressSpace())) {
			return false;
		}
		return min.compareTo(addr) <= 0 && addr.compareTo(max) <= 0;
	}

	/**
	 * Check if any token of the given line is part of the instruction at the PC
	 * 
	 * @param program the program the line was decompiled from
	 * @param line the line
	 * @return true if the line holds the PC
	 */
	boolean isPcLine(Program program, ClangLine line) {
		ProgramLocation pc = staticPc;
		if (pc == null || pc.getProgram() != program) {
			return false;
		}
		for (ClangToken token : line.getAllTokens()) {
			if (isPcToken(token)) {
				return true;
			}
		}
		return false;
	}
}
