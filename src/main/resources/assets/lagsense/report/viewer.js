"use strict";
(() => {
  const $ = (id) => document.getElementById(id),
    make = (tag, text, cls) => {
      const e = document.createElement(tag);
      if (text != null) e.textContent = String(text);
      if (cls) e.className = cls;
      return e;
    };
  const num = (v) => (Number.isFinite(Number(v)) ? Number(v) : 0);
  const number = (v, places = 2) =>
    v == null || !Number.isFinite(Number(v))
      ? "-"
      : Number(v).toLocaleString(undefined, {
          minimumFractionDigits: places,
          maximumFractionDigits: places,
        });
  const integer = (v) => number(v, 0),
    when = (v) =>
      num(v) > 0
        ? new Date(num(v)).toLocaleString(undefined, {
            dateStyle: "medium",
            timeStyle: "medium",
          })
        : "Not recorded";
  const dateHint = (v) =>
    num(v) > 0 ? new Date(num(v)).toISOString() : "Not recorded";
  const state = {
    view: "summary",
    search: "",
    dimension: "All",
    category: "All",
    scope: "server",
    group: "instances",
    sort: "cost",
    direction: "all",
    page: 0,
    pageSize: 100,
    expanded: new Set(),
  };
  let meta,
    worker,
    token = 0,
    pending = new Map(),
    latestQuery = 0,
    selected = null,
    lastRows = [],
    mapState = { x: 0, z: 0, scale: 14 },
    mapToken = 0,
    selectedChunk = null,
    searchTimer,
    mapTimer,
    fitSerial = 0;
  const palette = () => getComputedStyle(document.documentElement),
    colour = (name) =>
      palette()
        .getPropertyValue("--" + name)
        .trim();
  let stops = [];
  function refreshColours() {
    stops = colour("timing-colours")
      .split(",")
      .map((c) => c.trim());
  }
  function gradient(f) {
    if (!(f > 0)) return stops[0];
    if (f >= 1) return stops[4];
    const p = f * 4,
      i = Math.floor(p),
      t = p - i,
      a = parseInt(stops[i].slice(1), 16),
      b = parseInt(stops[i + 1].slice(1), 16);
    return (
      "#" +
      [16, 8, 0]
        .map((s) =>
          Math.round(
            ((a >> s) & 255) + (((b >> s) & 255) - ((a >> s) & 255)) * t,
          )
            .toString(16)
            .padStart(2, "0"),
        )
        .join("")
    );
  }
  function valueColour(row) {
    return row.metric === "delta"
      ? row.value > 0
        ? colour("warning")
        : row.value < 0
          ? colour("improvement")
          : colour("text")
      : row.metric === "sample"
        ? colour("text")
        : gradient(Math.log1p(Math.max(0, num(row.value))) / Math.log1p(10000));
  }
  function request(cmd, args = {}) {
    const id = ++token;
    return new Promise((resolve, reject) => {
      pending.set(id, { resolve, reject });
      worker.postMessage({ cmd, token: id, ...args });
    });
  }
  function activity(message) {
    $("activity").textContent = message;
  }
  function fatal(error) {
    activity("");
    const box = make("div", null, "notice error");
    box.append(
      make("strong", "Could not open report"),
      make("p", String(error)),
    );
    $("notices").append(box);
  }
  function notice(title, text) {
    const box = make("div", null, "notice");
    box.append(make("strong", title), make("p", text));
    $("notices").append(box);
  }
  function options(id, items, value) {
    const element = $(id);
    element.replaceChildren(
      ...items.map((item) => {
        const e = make("option", typeof item === "string" ? item : item.label);
        e.value = typeof item === "string" ? item : item.value;
        return e;
      }),
    );
    element.value = value;
    if (!element.value && items.length) element.selectedIndex = 0;
    return element.value;
  }
  function metadata(fields) {
    const dl = make("dl", null, "metadata");
    for (const [label, value] of fields)
      if (value != null && value !== "") {
        dl.append(make("dt", label), make("dd", value));
      }
    return dl;
  }
  function stat(label, value, unit) {
    const dl = make("dl", null, "stat"),
      dd = make("dd", value);
    if (unit) dd.append(make("small", unit));
    dl.append(make("dt", label), dd);
    return dl;
  }
  function block(title) {
    const section = make("section", null, "summary-block");
    section.append(make("h3", title));
    return section;
  }
  function summaryList(title, items, open) {
    const section = block(title),
      ul = make("ul", null, "mini-list");
    for (const item of items) {
      const li = make("li"),
        name = make("button", item.name, "name");
      name.type = "button";
      name.title = item.name + (item.position ? "\n" + item.position : "");
      name.onclick = () => open(item);
      const value = make("span", number(item.value) + " µs/t", "value");
      value.style.color = valueColour({ metric: "time", value: item.value });
      li.append(name, value);
      ul.append(li);
    }
    if (!items.length)
      ul.append(make("li", "No measurements recorded.", "muted"));
    section.append(ul);
    return section;
  }
  function showNotices() {
    const s = meta.summary;
    $("notices").replaceChildren();
    if (s.truncated || s.incomplete || s.presentationLimited)
      notice(
        "Incomplete capture data",
        s.presentationLimited
          ? "The original comparison was a limited preview."
          : s.truncated
            ? "Some measurements are missing because capture or report limits were reached."
            : "At least one capture is incomplete, so a missing result does not mean the object was removed.",
      );
    for (const text of s.notes || s.limits || []) notice("Capture note", text);
    if (s.backgroundLimited)
      notice(
        "Background samples incomplete",
        s.backgroundReason ||
          "Some background samples could not be saved because a capture limit was reached.",
      );
    if (s.serverLimited || (s.kind === "cpu" && s.limited))
      notice(
        "Call paths incomplete",
        "Some call paths could not be saved because a capture limit was reached.",
      );
    if (s.chunkPartial)
      notice(
        "Incomplete chunk details",
        `This file has details for ${integer(s.targets)} of ${integer(s.chunkTargets)} results in this chunk. Its total includes results whose details are missing.`,
      );
  }
  function renderOverview() {
    const s = meta.summary,
      overview = $("overview");
    overview.replaceChildren();
    const stats = make("div", null, "summary-stats"),
      grid = make("div", null, "summary-grid");
    if (s.kind === "comparison") {
      $("report-title").textContent = "What changed?";
      $("capture-context").textContent =
        `Earlier: ${when(s.beforeAt)} · Later: ${when(s.afterAt)}`;
      const delta = num(s.afterMspt) - num(s.beforeMspt);
      stats.append(
        stat("Earlier server time", number(s.beforeMspt), "MSPT"),
        stat("Later server time", number(s.afterMspt), "MSPT"),
        stat("Server change", (delta > 0 ? "+" : "") + number(delta), "ms/t"),
        stat(
          "Captured ticks",
          integer(s.beforeTicks) + " → " + integer(s.afterTicks),
        ),
      );
      const info = block("Compared captures");
      info.append(
        metadata([
          ["Earlier capture", String(s.beforeId)],
          ["Earlier timestamp", dateHint(s.beforeAt)],
          ["Later capture", String(s.afterId)],
          ["Later timestamp", dateHint(s.afterAt)],
          ["Direction", "Later minus earlier"],
          ["Normalisation", "Each side uses its own captured tick count."],
          [
            "Individual details",
            s.hasDetails
              ? "Expand a result to see the measurements from each capture."
              : "This older comparison has no saved individual results.",
          ],
        ]),
      );
      info.classList.add("wide");
      grid.append(info);
      const explore = block("Investigate changes");
      for (const [view, label] of [
        ["changes-types", "Types → individual contributors"],
        ["changes-chunks", "Chunks → categories → types → contributors"],
      ]) {
        const b = make("button", label);
        b.onclick = () => navigate(view);
        b.style.marginRight = "10px";
        b.style.marginBottom = "8px";
        explore.append(b);
      }
      explore.classList.add("wide");
      grid.append(explore);
    } else if (s.kind === "cpu") {
      $("report-title").textContent = "Captured call paths";
      $("capture-context").textContent = s.thread || "Recorded thread";
      stats.append(
        stat("Java execution samples", integer(s.samples)),
        stat("Saved stack samples", integer(s.retained)),
        stat("Capture state", s.state || "Unavailable"),
      );
      const info = block("Sampling context");
      info.classList.add("wide");
      info.append(
        metadata([
          ["Thread", s.thread || "Not recorded"],
          ["Measurement", "Sampled Java execution"],
          [
            "Percentages",
            "Percentages show each method’s share of the saved CPU samples rather than CPU usage or elapsed time.",
          ],
        ]),
      );
      grid.append(info);
    } else if (s.kind === "frames") {
      $("report-title").textContent = "Client frame profile";
      $("capture-context").textContent = when(s.capturedAt);
      stats.append(
        stat("Average frame rate", number(s.fps, 1), "FPS"),
        stat("1% low", number(s.onePercentLow, 1), "FPS"),
        stat("Frame intervals", integer(s.frames)),
        stat("Capture duration", number(s.seconds, 2), "s"),
      );
      const info = block("Frame intervals");
      info.append(
        metadata([
          ["Median ms/frame", number(s.medianMs)],
          ["95th percentile ms/frame", number(s.p95Ms)],
          ["99th percentile ms/frame", number(s.p99Ms)],
          ["Worst ms/frame", number(s.worstMs)],
          ["Stutters over 50 ms", integer(s.stutters)],
        ]),
      );
      grid.append(info);
      const gpu = block("GPU timing");
      gpu.append(
        metadata([
          ["State", s.gpu?.status || "Not recorded"],
          ["Samples", s.gpu?.samples],
          [
            "Mean ms/frame",
            num(s.gpu?.samples) > 0 ? number(s.gpu.meanMs) : null,
          ],
          [
            "95th percentile ms/frame",
            num(s.gpu?.samples) > 0 ? number(s.gpu.p95Ms) : null,
          ],
        ]),
      );
      grid.append(gpu);
      const stages = block("CPU stages");
      stages.classList.add("wide");
      stages.append(
        metadata(
          (s.stages || []).map((v) => [
            v.name,
            number(v.msPerFrame) + " ms/frame · " + integer(v.calls) + " calls",
          ]),
        ),
      );
      grid.append(stages);
      if (s.graphMs?.length) {
        const graph = block("Slowest frames over the capture");
        graph.classList.add("wide");
        const svg = document.createElementNS(
          "http://www.w3.org/2000/svg",
          "svg",
        );
        svg.classList.add("spark");
        svg.setAttribute("viewBox", "0 0 900 130");
        svg.setAttribute("role", "img");
        svg.setAttribute(
          "aria-label",
          "Slowest frame in each part of the capture, in milliseconds",
        );
        const max = Math.max(1, ...s.graphMs.map(num)),
          path = document.createElementNS(svg.namespaceURI, "path");
        path.setAttribute(
          "d",
          s.graphMs
            .map(
              (v, i) =>
                (i ? "L" : "M") +
                ((i * 900) / Math.max(1, s.graphMs.length - 1)).toFixed(2) +
                "," +
                (120 - (num(v) / max) * 110).toFixed(2),
            )
            .join(" "),
        );
        svg.append(path);
        graph.append(
          svg,
          make(
            "p",
            "Slowest frame in each group · " +
              number(max) +
              " ms at the top of the chart",
            "muted tiny",
          ),
        );
        grid.append(graph);
      }
    } else {
      $("report-title").textContent =
        s.kind === "chunk"
          ? "Chunk contributors"
          : s.admin
            ? "Server profile"
            : "Base Health";
      $("capture-context").textContent =
        s.kind === "chunk"
          ? `${integer(s.chunkTargets)} recorded targets · ${integer(s.ticks)} ticks`
          : `${when(s.capturedAt)} · ${integer(s.ticks)} ticks · capture ${s.id}`;
      if (s.admin && s.kind !== "chunk")
        stats.append(
          stat("Server tick time", number(s.mspt), "MSPT"),
          stat(
            "Server tick rate",
            num(s.tps) > 0 ? number(s.tps, 1) : "-",
            "TPS",
          ),
        );
      stats.append(
        stat("Measured results", integer(s.targets)),
        stat("Measured chunks", integer(s.chunkCount)),
      );
      const info = block("Capture details");
      info.append(
        metadata([
          ["Capture ID", s.id],
          ["Captured at", s.capturedAt ? dateHint(s.capturedAt) : null],
          ["Captured ticks", s.ticks],
          ["Trigger", s.trigger || "Not recorded"],
          [
            "Scope",
            s.admin ? "Administrator report" : "Authorised targets only",
          ],
          ["Dimensions", s.dimensions],
          [
            "Profiler overhead",
            s.bookkeepingNanos != null
              ? number(
                  num(s.bookkeepingNanos) / 1e6 / Math.max(1, num(s.ticks)),
                ) + " ms/t"
              : null,
          ],
        ]),
      );
      grid.append(info);
      if (s.coverage) {
        const c = s.coverage,
          total = num(c.totalNanos),
          coverage = block("Server tick time breakdown"),
          bar = make("div", null, "coverage-bar"),
          legend = make("div", null, "coverage-legend");
        const pieces = [
          [
            "Targets only",
            num(c.targetNanos) - num(c.overlapNanos),
            colour("accent"),
          ],
          [
            "Events only",
            num(c.eventNanos) - num(c.overlapNanos),
            colour("secondary"),
          ],
          [
            "Time shared by targets and events",
            num(c.overlapNanos),
            colour("buttonLight"),
          ],
          ["Other server time", num(c.outsideNanos), colour("muted")],
        ];
        for (const [name, value, color] of pieces) {
          const segment = make("span");
          segment.style.width =
            (total > 0 ? (Math.max(0, value) / total) * 100 : 0) + "%";
          segment.style.backgroundColor = color;
          segment.title = `${name}: ${number(value / 1e6 / Math.max(1, num(s.ticks)))} ms/t`;
          bar.append(segment);
          const label = make("span"),
            swatch = make("span", null, "swatch");
          swatch.style.backgroundColor = color;
          label.append(swatch, document.createTextNode(name));
          legend.append(
            label,
            make(
              "span",
              number(value / 1e6 / Math.max(1, num(s.ticks))) + " ms/t",
              "mono",
            ),
            make(
              "span",
              number(total > 0 ? (value / total) * 100 : 0, 1) + "%",
              "mono",
            ),
          );
        }
        coverage.append(
          bar,
          legend,
          make(
            "p",
            "Time measured in both targets and events is counted once.",
            "metric-note",
          ),
        );
        grid.append(coverage);
      } else {
        const unavailable = block("Server tick time breakdown");
        unavailable.append(
          make(
            "p",
            "This capture does not record how much tick time falls outside the measured targets and events.",
            "muted",
          ),
        );
        grid.append(unavailable);
      }
      grid.append(
        summaryList("Highest measured targets", s.topTargets || [], (item) => {
          navigate("targets");
          $("search").value = item.name;
          state.search = item.name;
          loadRows();
        }),
        summaryList("Highest measured chunks", s.topChunks || [], (item) => {
          navigate("chunks");
          state.dimension = item.dimension;
          $("dimension").value = item.dimension;
          state.search = item.name;
          $("search").value = item.name;
          loadRows();
        }),
      );
      if (s.kind !== "chunk") {
        const sampling = block("Captured diagnostics");
        sampling.classList.add("wide");
        sampling.append(
          metadata([
            ["Timed event handlers", s.events],
            [
              "Server Java execution samples",
              s.serverSamples ?? "Not recorded",
            ],
            [
              "Background Java execution samples",
              s.backgroundSamples ?? "Not recorded",
            ],
            ["Server sampling state", s.serverState || "Not recorded"],
            ["Background sampling state", s.backgroundState || "Not recorded"],
            ["Event timing state", s.callbackState || "Not recorded"],
          ]),
        );
        grid.append(sampling);
      }
    }
    overview.append(stats, grid);
  }
  function navigation() {
    const views =
      meta.kind === "comparison"
        ? [
            ["summary", "Summary"],
            ["changes-types", "Types"],
            ["changes-chunks", "Chunks"],
          ]
        : meta.kind === "cpu"
          ? [
              ["summary", "Summary"],
              ["cpu", "CPU activity"],
              ["paths", "Call paths"],
            ]
          : meta.kind === "frames"
            ? [["summary", "Summary"]]
            : [
                ["summary", "Summary"],
                ["targets", "Targets"],
                ["chunks", "Chunks"],
                ["map", "Map"],
                ...(meta.kind === "profile" && meta.summary.admin
                  ? [
                      ["cpu", "CPU activity"],
                      ["paths", "Call paths"],
                      ["events", "Event timings"],
                    ]
                  : []),
              ];
    $("navigation").replaceChildren(
      ...views.map(([id, label]) => {
        const b = make("button", label);
        b.type = "button";
        b.dataset.view = id;
        b.setAttribute("aria-current", state.view === id ? "page" : "false");
        b.onclick = () => navigate(id);
        return b;
      }),
    );
  }
  function navigate(view) {
    state.view = view;
    state.page = 0;
    state.search = "";
    state.category = "All";
    state.dimension = "All";
    state.direction = "all";
    state.group =
      view === "targets"
        ? "instances"
        : view === "paths"
          ? "hierarchy"
          : view === "cpu" || view === "events"
            ? "mod"
            : "hierarchy";
    state.sort = "cost";
    state.expanded.clear();
    selected = null;
    $("selection").hidden = true;
    $("search").value = "";
    state.scope = meta.kind === "cpu" ? "capture" : "server";
    for (const b of $("navigation").children)
      b.setAttribute(
        "aria-current",
        b.dataset.view === view ? "page" : "false",
      );
    $("overview").hidden = view !== "summary";
    $("explorer").hidden = view === "summary";
    if (view !== "summary") {
      controls();
      loadRows();
      if (view === "map") fitMap();
    } else activity("");
  }
  function controls() {
    const view = state.view,
      cpu = view === "cpu" || view === "paths",
      comparison = view.startsWith("changes-"),
      locations = [
        "targets",
        "chunks",
        "map",
        "changes-types",
        "changes-chunks",
      ].includes(view);
    $("scope-label").hidden = !cpu;
    $("dimension-label").hidden = cpu || view === "events";
    $("kind-label").hidden = ![
      "targets",
      "changes-types",
      "changes-chunks",
    ].includes(view);
    $("direction-label").hidden = !comparison;
    $("group-label").hidden = !["targets", "cpu", "paths", "events"].includes(
      view,
    );
    $("map").hidden = view !== "map";
    if (cpu)
      state.scope = options(
        "scope",
        view === "paths" ? meta.pathScopes : meta.scopes,
        state.scope,
      );
    const dims = view === "map" ? meta.dimensions : ["All", ...meta.dimensions];
    state.dimension = options("dimension", dims, state.dimension);
    state.category = options(
      "category",
      ["All", ...meta.categories],
      state.category,
    );
    const groupOptions =
      view === "targets"
        ? [
            { value: "instances", label: "By instance" },
            { value: "type", label: "By type" },
            { value: "mod", label: "By mod" },
            { value: "mod-type", label: "By Mod → Type" },
          ]
        : view === "paths"
          ? [
              { value: "hierarchy", label: "Call hierarchy" },
              { value: "methods", label: "Running code" },
            ]
          : [
              { value: "mod", label: "By mod" },
              { value: "actions", label: "By action" },
            ];
    state.group = options("group", groupOptions, state.group);
    state.sort = options(
      "sort",
      [
        {
          value: "cost",
          label: comparison
            ? "Largest change"
            : cpu
              ? "Most samples"
              : "Most time",
        },
        ...(comparison ? [{ value: "later", label: "Later cost" }] : []),
        { value: "name", label: "Name" },
      ],
      state.sort,
    );
    $("direction").value = state.direction;
    const label = {
      targets: "Measured targets",
      chunks: "Chunk contributors",
      map: "Measured chunk map",
      cpu: "CPU activity",
      paths: "Call paths",
      events: "Event timings",
      "changes-types": "Type changes",
      "changes-chunks": "Chunk changes",
    }[view];
    $("result-title").textContent = label;
    $("metric-note").textContent = comparison
      ? "Changes compare time per tick in each capture. Added and removed describe the results, not what happened in the world."
      : cpu
        ? "Percentages show how often code appeared in the CPU samples rather than how long it ran."
        : view === "events"
          ? "Handler timings include code they call, so some work can appear in more than one result."
          : "Average microseconds per server tick. Some work may be included in more than one result.";
    const head = make("tr");
    head.append(
      make("th", "Name", "name-cell"),
      make(
        "th",
        comparison
          ? "Recorded contributions"
          : cpu
            ? "Samples"
            : view === "events"
              ? "Dispatches"
              : "Recorded activity",
        "number count",
      ),
    );
    if (comparison)
      head.append(
        make("th", "Earlier µs/t", "number before"),
        make("th", "Later µs/t", "number after"),
      );
    head.append(
      make("th", comparison ? "Change µs/t" : cpu ? "Share" : "µs/t", "number"),
      make(
        "th",
        cpu ? "Thread / mod" : view === "events" ? "Mod" : "Context",
        "context",
      ),
    );
    head.append(make("th", "Actions", "location"));
    $("result-columns").replaceChildren(head);
    $("results").classList.toggle("comparison", comparison);
  }
  let latestPage;
  async function loadRows() {
    const id = ++latestQuery;
    activity("Preparing results…");
    $("previous").disabled = true;
    $("next").disabled = true;
    try {
      const response = await request("query", {
        query: { ...state, expanded: [...state.expanded] },
      });
      if (id !== latestQuery) return;
      if (response.page !== state.page) {
        state.page = response.page;
        return loadRows();
      }
      latestPage = response;
      lastRows = response.rows;
      renderRows(response);
      activity("");
    } catch (error) {
      if (id === latestQuery) {
        activity("Could not prepare results: " + error.message);
      }
    }
  }
  function countText(row) {
    const unit =
      row.metric === "sample"
        ? "samples"
        : state.view === "events"
          ? "calls"
          : row.metric === "delta"
            ? "records"
            : row.chunk
              ? "targets"
              : row.expandable
                ? "records"
                : "updates";
    return integer(row.count) + " " + unit;
  }
  function renderRows(response) {
    const body = $("result-rows");
    body.replaceChildren();
    const locations = [
        "targets",
        "chunks",
        "map",
        "changes-types",
        "changes-chunks",
      ].includes(state.view),
      comparison = state.view.startsWith("changes-");
    for (const row of response.rows) {
      const tr = make("tr");
      if (selected === row.id) tr.classList.add("selected");
      tr.dataset.record = row.id;
      const name = make("td", null, "name-cell"),
        wrap = make("div", null, "row-name");
      wrap.style.paddingLeft = Math.min(row.depth * 8, 160) + "px";
      if (row.expandable) {
        tr.classList.add("expandable");
        tr.setAttribute("aria-expanded", String(row.expanded));
        tr.onclick = (event) => {
          if (!event.target.closest("button,input,select,a")) {
            state.expanded.has(row.id)
              ? state.expanded.delete(row.id)
              : state.expanded.add(row.id);
            loadRows();
          }
        };
        const branch = make("button", row.expanded ? "−" : "+", "branch");
        branch.type = "button";
        branch.setAttribute("aria-expanded", String(row.expanded));
        branch.setAttribute(
          "aria-label",
          (row.expanded ? "Collapse " : "Expand ") + row.name,
        );
        branch.onclick = () => {
          state.expanded.has(row.id)
            ? state.expanded.delete(row.id)
            : state.expanded.add(row.id);
          loadRows();
        };
        wrap.append(branch);
      } else wrap.append(make("span", null, "leaf-spacer"));
      const label = make("span", row.name, "row-label");
      label.title =
        row.name +
        (row.context ? "\n" + row.context : "") +
        (row.depth ? "\nHierarchy depth: " + row.depth : "");
      wrap.append(label);
      name.append(wrap);
      tr.append(name, make("td", countText(row), "number count"));
      if (comparison)
        tr.append(
          make("td", number(row.before), "number before"),
          make("td", number(row.after), "number after"),
        );
      const value = make(
        "td",
        row.metric === "sample"
          ? number(row.total > 0 ? (row.value / row.total) * 100 : 0, 1) + "%"
          : (row.metric === "delta" && row.value > 0 ? "+" : "") +
              number(row.value),
        "number value",
      );
      value.style.color = valueColour(row);
      value.title =
        row.metric === "sample"
          ? `${integer(row.value)} / ${integer(row.total)} samples`
          : "Captured measurement";
      tr.append(value);
      const context = make("td", row.context, "context");
      context.title = row.context;
      tr.append(context);
      const actions = make("td", null, "location"),
        details = make("button", "Details");
      details.type = "button";
      details.setAttribute("aria-label", "Details for " + row.name);
      details.onclick = () => showDetail(row.id);
      actions.append(details);
      if (row.tp) {
        const copy = make("button", "Copy TP Command");
        copy.type = "button";
        copy.title =
          "Copy a command to paste into Minecraft. Operator permission is required, and the server checks for a safe landing near the saved coordinates.";
        copy.onclick = () => copyCommand(row.tp.command, copy);
        actions.append(copy);
      }
      tr.append(actions);
      body.append(tr);
    }
    const count = response.total,
      size = response.pageSize,
      page = response.page,
      pages = Math.max(1, Math.ceil(count / size));
    $("result-count").textContent =
      integer(count) +
      (state.expanded.size ? " visible hierarchy rows" : " results");
    $("page-status").textContent = count
      ? `${integer(page * size + 1)}–${integer(Math.min(count, (page + 1) * size))} of ${integer(count)}`
      : "0 results";
    $("page-number").value = page + 1;
    $("page-number").max = pages;
    $("page-total").textContent = "of " + integer(pages);
    $("previous").disabled = page === 0;
    $("next").disabled = page + 1 >= pages;
    $("collapse").disabled = state.expanded.size === 0;
    $("empty").hidden = count > 0;
    $("empty").textContent = response.empty || "No matching results.";
    $("results").hidden = count === 0;
  }
  async function showDetail(id) {
    selected = id;
    const result = await request("detail", { id });
    if (!result || selected !== id) return;
    $("selection").hidden = false;
    $("detail-title").textContent = result.title;
    const fields = make("div");
    fields.append(
      metadata(
        result.fields.map(([name, value]) => [
          name,
          typeof value === "number"
            ? number(value, Number.isInteger(value) ? 0 : 6)
            : (value ?? "Not recorded"),
        ]),
      ),
    );
    if (result.tp) {
      const copy = make("button", "Copy TP Command");
      copy.style.marginTop = "12px";
      copy.onclick = () => copyCommand(result.tp.command, copy);
      fields.append(copy);
    }
    const links = result.links || [];
    if (links.length) {
      const container = make("div", null, "detail-links"),
        heading = make("h3", "Captured relationships");
      container.append(heading);
      let index = 0;
      const add = () => {
        const end = Math.min(index + 50, links.length);
        for (; index < end; index++) {
          const link = links[index],
            b = make("button", link.label);
          b.onclick = () => {
            navigate(link.view);
            state.scope = link.scope;
            $("scope").value = state.scope;
            state.group = link.group;
            $("group").value = state.group;
            state.search = link.search;
            $("search").value = link.search;
            loadRows();
          };
          container.append(b);
        }
        more.hidden = index >= links.length;
        more.textContent =
          "More relationships (" + integer(links.length - index) + ")";
      };
      const more = make("button", "More relationships");
      more.onclick = () => {
        more.remove();
        add();
        container.append(more);
      };
      add();
      container.append(more);
      fields.append(container);
    }
    $("detail-body").replaceChildren(fields);
    for (const row of $("result-rows").children)
      row.classList.toggle("selected", row.dataset.record === id);
  }
  async function copyCommand(command, button) {
    try {
      let copied = false;
      if (navigator.clipboard?.writeText)
        try {
          await navigator.clipboard.writeText(command);
          copied = true;
        } catch (ignore) {}
      if (!copied) {
        const text = make("textarea");
        text.value = command;
        text.readOnly = true;
        text.style.position = "fixed";
        text.style.left = "-10000px";
        document.body.append(text);
        text.select();
        try {
          copied = document.execCommand("copy");
        } catch (ignore) {}
        text.remove();
      }
      if (copied) {
        const original = button.textContent;
        button.textContent = "Copied!";
        button.setAttribute("aria-live", "polite");
        setTimeout(() => {
          if (button.isConnected) button.textContent = original;
        }, 1300);
        return;
      }
      let text = $("manual-copy");
      if (!text) {
        text = make("input");
        text.id = "manual-copy";
        text.readOnly = true;
        text.setAttribute("aria-label", "Teleport command to copy");
        text.style.width = "100%";
        text.style.marginTop = "10px";
        $("selection").hidden = false;
        $("detail-title").textContent = "Copy teleport command";
        $("detail-body").replaceChildren(
          make(
            "p",
            "Your browser blocked clipboard access. Copy the selected command below.",
          ),
          text,
        );
      }
      text.value = command;
      text.focus();
      text.select();
    } catch (error) {
      activity(
        "Could not copy command. Select the recorded location to try again.",
      );
    }
  }
  function refreshResults() {
    state.page = 0;
    state.expanded.clear();
    selected = null;
    $("selection").hidden = true;
    loadRows();
    if (state.view === "map") fitMap();
  }
  function installControls() {
    $("theme").value = document.documentElement.dataset.theme;
    $("theme").onchange = () => {
      document.documentElement.dataset.theme = $("theme").value;
      refreshColours();
      renderOverview();
      if (latestPage) renderRows(latestPage);
      if (state.view === "map") drawMap();
    };
    $("search").oninput = () => {
      clearTimeout(searchTimer);
      searchTimer = setTimeout(() => {
        state.search = $("search").value;
        refreshResults();
      }, 180);
    };
    for (const [id, field] of [
      ["scope", "scope"],
      ["dimension", "dimension"],
      ["category", "category"],
      ["group", "group"],
      ["sort", "sort"],
      ["direction", "direction"],
    ])
      $(id).onchange = () => {
        state[field] = $(id).value;
        refreshResults();
      };
    $("collapse").onclick = () => {
      state.expanded.clear();
      loadRows();
    };
    $("previous").onclick = () => {
      if (state.page > 0) {
        state.page--;
        loadRows();
      }
    };
    $("next").onclick = () => {
      state.page++;
      loadRows();
    };
    $("page-number").onchange = () => {
      state.page = Math.max(0, num($("page-number").value) - 1);
      loadRows();
    };
    $("page-size").onchange = () => {
      state.pageSize = num($("page-size").value);
      state.page = 0;
      loadRows();
    };
    $("detail-close").onclick = () => {
      selected = null;
      $("selection").hidden = true;
      for (const row of $("result-rows").children)
        row.classList.remove("selected");
    };
    $("map-fit").onclick = fitMap;
    $("map-plus").onclick = () => zoomMap(1.35);
    $("map-minus").onclick = () => zoomMap(1 / 1.35);
    const canvas = $("chunk-map");
    let drag = null,
      lastHover = 0;
    canvas.addEventListener(
      "wheel",
      (e) => {
        e.preventDefault();
        const rect = canvas.getBoundingClientRect();
        zoomMap(
          Math.exp(-Math.max(-150, Math.min(150, e.deltaY)) * 0.006),
          e.clientX - rect.left,
          e.clientY - rect.top,
        );
      },
      { passive: false },
    );
    canvas.addEventListener("pointerdown", (e) => {
      drag = {
        x: e.clientX,
        y: e.clientY,
        startX: e.clientX,
        startY: e.clientY,
      };
      canvas.setPointerCapture(e.pointerId);
    });
    canvas.addEventListener("pointermove", (e) => {
      if (drag) {
        const dx = e.clientX - drag.x,
          dz = e.clientY - drag.y;
        mapState.x -= dx / mapState.scale;
        mapState.z -= dz / mapState.scale;
        drag.x = e.clientX;
        drag.y = e.clientY;
        scheduleMap();
      } else if (performance.now() - lastHover > 70) {
        lastHover = performance.now();
        pickMap(e, false);
      }
    });
    canvas.addEventListener("pointerup", (e) => {
      const click =
        drag &&
        Math.hypot(e.clientX - drag.startX, e.clientY - drag.startY) < 4;
      drag = null;
      if (click) pickMap(e, true);
    });
    canvas.addEventListener("pointercancel", () => {
      drag = null;
    });
    canvas.onkeydown = (e) => {
      if (e.key === "+" || e.key === "=") {
        zoomMap(1.35);
        e.preventDefault();
      } else if (e.key === "-") {
        zoomMap(1 / 1.35);
        e.preventDefault();
      } else if (
        ["ArrowLeft", "ArrowRight", "ArrowUp", "ArrowDown"].includes(e.key)
      ) {
        const d = 8 / mapState.scale;
        if (e.key === "ArrowLeft") mapState.x -= d;
        if (e.key === "ArrowRight") mapState.x += d;
        if (e.key === "ArrowUp") mapState.z -= d;
        if (e.key === "ArrowDown") mapState.z += d;
        scheduleMap();
        e.preventDefault();
      }
    };
    new ResizeObserver(() => {
      if (state.view === "map") scheduleMap();
    }).observe(canvas);
  }
  function dimensions() {
    const canvas = $("chunk-map"),
      rect = canvas.getBoundingClientRect();
    return {
      width: Math.max(1, Math.round(rect.width)),
      height: Math.max(1, Math.round(rect.height)),
    };
  }
  function scheduleMap() {
    clearTimeout(mapTimer);
    mapTimer = setTimeout(drawMap, 25);
  }
  async function fitMap() {
    if (state.view !== "map") return;
    const ticket = ++fitSerial,
      dimension = state.dimension,
      bounds = await request("bounds", { dimension });
    if (state.dimension !== dimension || ticket !== fitSerial) return;
    const { width, height } = dimensions();
    if (bounds) {
      mapState.x = (bounds.minX + bounds.maxX + 1) / 2;
      mapState.z = (bounds.minZ + bounds.maxZ + 1) / 2;
      mapState.scale = Math.max(
        0.000001,
        Math.min(
          40,
          width / (bounds.maxX - bounds.minX + 3),
          height / (bounds.maxZ - bounds.minZ + 3),
        ),
      );
    }
    drawMap();
  }
  function zoomMap(factor, x, y) {
    const { width, height } = dimensions();
    x = x ?? width / 2;
    y = y ?? height / 2;
    const old = mapState.scale,
      next = Math.max(0.000001, Math.min(96, old * factor));
    mapState.x += (x - width / 2) * (1 / old - 1 / next);
    mapState.z += (y - height / 2) * (1 / old - 1 / next);
    mapState.scale = next;
    scheduleMap();
  }
  async function drawMap() {
    if (state.view !== "map") return;
    const id = ++mapToken,
      { width, height } = dimensions(),
      snapshot = { ...mapState },
      response = await request("map", {
        query: { ...snapshot, width, height, dimension: state.dimension },
      });
    if (id !== mapToken || state.view !== "map") return;
    const canvas = $("chunk-map"),
      ratio = Math.min(2, devicePixelRatio || 1);
    canvas.width = Math.round(width * ratio);
    canvas.height = Math.round(height * ratio);
    const ctx = canvas.getContext("2d");
    ctx.scale(ratio, ratio);
    ctx.fillStyle = colour("background");
    ctx.fillRect(0, 0, width, height);
    for (const tile of response.tiles) {
      ctx.fillStyle = valueColour({ metric: "time", value: tile.value });
      const gap = tile.size >= 7 ? 1 : 0;
      ctx.fillRect(
        tile.x,
        tile.z,
        Math.max(1, tile.size - gap),
        Math.max(1, tile.size - gap),
      );
    }
    if (selectedChunk && selectedChunk.dimension === state.dimension) {
      ctx.strokeStyle = colour("accent");
      ctx.lineWidth = 2;
      const x =
          (Math.floor(selectedChunk.x / 16) - snapshot.x) * snapshot.scale +
          width / 2,
        z =
          (Math.floor(selectedChunk.z / 16) - snapshot.z) * snapshot.scale +
          height / 2;
      ctx.strokeRect(
        x - 2,
        z - 2,
        Math.max(5, snapshot.scale + 3),
        Math.max(5, snapshot.scale + 3),
      );
    }
    $("map-context").textContent =
      `${integer(response.visible)} of ${integer(response.total)} measured chunks in view`;
    $("map-hint").textContent =
      response.scale < 3
        ? "Nearby chunks share a pixel at this zoom level, using the highest cost. The table lists them individually."
        : "Wheel to zoom · drag to pan · select a measured chunk";
  }
  async function pickMap(event, select) {
    const canvas = $("chunk-map"),
      rect = canvas.getBoundingClientRect(),
      { width, height } = dimensions(),
      x = mapState.x + (event.clientX - rect.left - width / 2) / mapState.scale,
      z = mapState.z + (event.clientY - rect.top - height / 2) / mapState.scale,
      c = await request("pick", {
        query: { dimension: state.dimension, x, z },
      });
    if (!c) {
      if (!select)
        $("map-hint").textContent = "No captured chunk at this position.";
      return;
    }
    $("map-hint").textContent =
      `${c.name} · ${c.dimension} · ${number(c.value)} µs/t · ${integer(c.targets)} targets`;
    if (select) {
      state.search = c.name;
      $("search").value = c.name;
      state.expanded.clear();
      state.page = 0;
      loadRows();
      selectedChunk = { dimension: c.dimension, x: c.x * 16, z: c.z * 16 };
      drawMap();
    }
  }
  async function start() {
    try {
      refreshColours();
      const source = $("viewer-worker").textContent,
        blob = new Blob([source], { type: "text/javascript" }),
        url = URL.createObjectURL(blob);
      worker = new Worker(url);
      URL.revokeObjectURL(url);
      $("viewer-worker").remove();
      worker.onmessage = (event) => {
        const p = pending.get(event.data.token);
        if (!p) return;
        pending.delete(event.data.token);
        event.data.error
          ? p.reject(new Error(event.data.error))
          : p.resolve(event.data.result);
      };
      worker.onerror = (event) => {
        const error = new Error(
          event.message || "Browser worker could not start.",
        );
        for (const p of pending.values()) p.reject(error);
        pending.clear();
        fatal(error.message);
      };
      const script = $("report-data");
      let raw = script.textContent;
      script.textContent = "";
      script.remove();
      const loading = request("init", { raw });
      raw = null;
      meta = await loading;
      navigation();
      installControls();
      renderOverview();
      showNotices();
      activity("");
      document.documentElement.dataset.ready = "true";
    } catch (error) {
      fatal(error.message || error);
      document.documentElement.dataset.failed = "true";
    }
  }
  start();
})();
