"use strict";
let documentData,
  data,
  mode,
  entries = [],
  tickCount = 0,
  chunks,
  config,
  serial = 0;
let roots = [],
  registry = new Map(),
  queryKey = "",
  treeRoot = [],
  activeCpu = null,
  activeMethods = new Map(),
  cpuChildren = new Map();
const num = (v) => (Number.isFinite(Number(v)) ? Number(v) : 0);
const list = (v) => (Array.isArray(v) ? v : []);
const key = (...parts) => JSON.stringify(parts);
const micro = (n, t = tickCount) => (t > 0 ? num(n) / 1000 / t : null);
const located = (e) =>
  e.locationKnown !== false &&
  e.dimension != null &&
  Number.isInteger(Number(e.x)) &&
  Number.isInteger(Number(e.y)) &&
  Number.isInteger(Number(e.z));
const owner = (e) =>
  String(e.type || "").includes(":")
    ? String(e.type).split(":", 1)[0]
    : "unattributed";
const match = (value, query) =>
  !query ||
  String(value ?? "")
    .toLowerCase()
    .includes(query);
const position = (e) =>
  located(e)
    ? `${e.dimension} · ${e.x} ${e.y} ${e.z}`
    : "Location not recorded";
function tpEntry(e) {
  if (
    !(
      ["Entity", "Block", "Chunk"].includes(e.kind) ||
      String(e.kind).startsWith("Random ") ||
      String(e.kind).startsWith("Scheduled ")
    ) ||
    !validDimension(e.type) ||
    !located(e) ||
    !validDimension(e.dimension) ||
    ![e.x, e.y, e.z].every((v) => Number.isInteger(Number(v))) ||
    Math.abs(num(e.x)) > 30000000 ||
    Math.abs(num(e.z)) > 30000000 ||
    Math.abs(num(e.y)) > 20000000
  )
    return null;
  return {
    command: `/lagsense tp location ${e.dimension} ${e.x} ${e.y} ${e.z}`,
    dimension: e.dimension,
    x: num(e.x),
    y: num(e.y),
    z: num(e.z),
    recorded: true,
  };
}
function validDimension(value) {
  return /^[a-z0-9_.-]+:[a-z0-9/._-]+$/.test(String(value));
}
function tpChunk(c) {
  const hotspot =
      c.original?.hotspot || c.refs.map((i) => entries[i]).find(located),
    hot = hotspot && tpEntry(hotspot);
  if (hot) return hot;
  if (
    !validDimension(c.dimension) ||
    !Number.isInteger(c.x) ||
    !Number.isInteger(c.z) ||
    Math.abs(c.x * 16 + 8) > 30000000 ||
    Math.abs(c.z * 16 + 8) > 30000000 ||
    !c.located
  )
    return null;
  return {
    command: `/lagsense tp chunk ${c.dimension} ${c.x} ${c.z}`,
    dimension: c.dimension,
    x: c.x * 16 + 8,
    z: c.z * 16 + 8,
    chunk: true,
  };
}
const category = (k) =>
  k === "Entity" ? "Entities" : k === "Block" ? "Ticking blocks" : k;
const sum = (refs, field) =>
  refs.reduce((n, i) => n + num(entries[i][field]), 0);
const split = (refs, by) => {
  const groups = new Map();
  for (const i of refs) {
    const id = by(entries[i]);
    if (!groups.has(id)) groups.set(id, []);
    groups.get(id).push(i);
  }
  return groups;
};
function node(name, value, metric, extra = {}) {
  const n = {
    id: "n" + ++serial,
    name: String(name ?? ""),
    value,
    metric,
    ...extra,
  };
  registry.set(n.id, n);
  return n;
}
function children(n) {
  if (!n.children) {
    n.children = n.build ? ordered(n.build()) : [];
    n.build = null;
  }
  return n.children;
}
function rowName(n) {
  return typeof n === "number" ? String(entries[n].type) : n.name;
}
function rowValue(n) {
  return typeof n === "number"
    ? micro(entries[n].nanos)
    : num(n.sortValue ?? (n.metric === "delta" ? Math.abs(n.value) : n.value));
}
function rowCount(n) {
  return typeof n === "number" ? num(entries[n].calls) : num(n.count);
}
function ordered(rows, sort = config?.sort || "cost") {
  const compare =
    sort === "name"
      ? (a, b) => rowName(a).localeCompare(rowName(b))
      : sort === "count"
        ? (a, b) => rowCount(b) - rowCount(a)
        : sort === "later"
          ? (a, b) => num(b.after) - num(a.after)
          : (a, b) => rowValue(b) - rowValue(a);
  return rows.sort(
    (a, b) => compare(a, b) || rowName(a).localeCompare(rowName(b)),
  );
}
function target(i, side = "") {
  const e = entries[i];
  return node(e.type, micro(e.nanos), "time", {
    entry: i,
    count: num(e.calls),
    context: e.dimension || "Unknown dimension",
    tp: tpEntry(e),
    id: "t" + i,
    detail: () => entryDetail(e),
    source: e,
    side,
  });
}
function entryDetail(e) {
  return [
    ["Registry ID", e.type],
    ["Category", category(e.kind)],
    ["Mod ID", owner(e)],
    ["Dimension", e.dimension || "Not recorded"],
    ["Position", located(e) ? `${e.x} ${e.y} ${e.z}` : "Not recorded"],
    [
      "Chunk",
      located(e)
        ? `${Math.floor(num(e.x) / 16)}, ${Math.floor(num(e.z) / 16)}`
        : "Not recorded",
    ],
    ["UUID", e.entityId || "Not recorded"],
    ["Captured updates", e.calls ?? "Not recorded"],
    ["Total measured nanoseconds", e.nanos],
    ["Captured ticks", tickCount],
    ["Measured µs/t", micro(e.nanos)],
    ...(!tpEntry(e)
      ? [
          [
            "Teleport command",
            "Unavailable because the saved coordinates are missing or invalid.",
          ],
        ]
      : []),
    [
      "Measurement",
      "Time spent updating this target, including any measured work it called.",
    ],
  ];
}
function targetGroup(name, refs, childKind = "targets", context = "") {
  return node(name, micro(sum(refs, "nanos")), "time", {
    count: refs.length,
    context,
    refs,
    detail: () => [
      ["Measured records", refs.length],
      ["Captured updates", sum(refs, "calls")],
      ["Combined µs/t", micro(sum(refs, "nanos"))],
      [
        "Aggregation",
        "Adds the saved target times, which can include some of the same work.",
      ],
    ],
    build: () =>
      childKind === "types"
        ? Array.from(
            split(refs, (e) => key(e.kind, e.type, e.dimension)),
            ([id, indices]) => {
              const p = JSON.parse(id);
              return targetGroup(
                p[1],
                indices,
                "targets",
                `${category(p[0])} · ${p[2]}`,
              );
            },
          )
        : refs.slice(),
  });
}
function buildChunks() {
  if (chunks) return chunks;
  chunks = new Map();
  for (let i = 0; i < entries.length; i++) {
    const e = entries[i];
    if (!located(e)) continue;
    const x = Math.floor(num(e.x) / 16),
      z = Math.floor(num(e.z) / 16),
      id = key(e.dimension, x, z);
    if (!chunks.has(id))
      chunks.set(id, {
        id,
        dimension: e.dimension,
        x,
        z,
        nanos: 0,
        calls: 0,
        refs: [],
        targets: 0,
        located: true,
      });
    const c = chunks.get(id);
    c.nanos += num(e.nanos);
    c.calls += num(e.calls);
    c.refs.push(i);
    c.targets++;
  }
  if (mode === "profile")
    for (const original of list(data.chunks)) {
      const id = key(original.dimension, num(original.x), num(original.z));
      let c = chunks.get(id);
      if (!c) {
        c = {
          id,
          dimension: original.dimension,
          x: num(original.x),
          z: num(original.z),
          refs: [],
          calls: 0,
          located: original.hotspot?.locationKnown !== false,
        };
        chunks.set(id, c);
      }
      c.nanos = num(original.nanos);
      c.targets = num(original.targets);
      c.original = original;
    }
  if (mode === "chunk") {
    const id = key(data.dimension, num(data.x), num(data.z));
    const c = chunks.get(id) || {
      id,
      dimension: data.dimension,
      x: num(data.x),
      z: num(data.z),
      refs: [],
      calls: 0,
      located: true,
    };
    c.nanos = num(data.nanos);
    c.targets = num(data.targets);
    c.partial = c.refs.length < c.targets || num(data.offset) > 0;
    chunks.set(id, c);
  }
  return chunks;
}
function chunkNode(c) {
  return node(`Chunk ${c.x}, ${c.z}`, micro(c.nanos), "time", {
    count: c.targets,
    context: c.dimension,
    chunk: c.id,
    tp: tpChunk(c),
    detail: () => [
      ["Dimension", c.dimension],
      ["Chunk coordinates", `${c.x}, ${c.z}`],
      ["Measured records", c.targets],
      ["Results with saved coordinates", c.refs.length],
      ["Measured µs/t", micro(c.nanos)],
      [
        "Contributor availability",
        c.refs.length < c.targets
          ? "Details are missing for some results in this chunk."
          : "All saved results with coordinates are included.",
      ],
    ],
    build: () =>
      Array.from(
        split(c.refs, (e) => e.kind),
        ([kind, refs]) =>
          targetGroup(category(kind), refs, "types", c.dimension),
      ),
  });
}
function filterEntry(e, q) {
  return (
    (q.dimension === "All" || e.dimension === q.dimension) &&
    (q.category === "All" || e.kind === q.category) &&
    (!q.search ||
      match(
        `${e.type} ${e.kind} ${e.dimension} ${e.entityId || ""} ${e.x} ${e.y} ${e.z} ${located(e) ? "Chunk " + Math.floor(num(e.x) / 16) + ", " + Math.floor(num(e.z) / 16) : ""}`,
        q.search,
      ))
  );
}
function targetRoots(q) {
  const refs = [];
  for (let i = 0; i < entries.length; i++)
    if (filterEntry(entries[i], q)) refs.push(i);
  if (q.group === "mod" || q.group === "mod-type")
    return Array.from(split(refs, owner), ([name, indices]) =>
      targetGroup(
        name,
        indices,
        q.group === "mod-type" ? "types" : "targets",
        "Mod",
      ),
    );
  if (q.group === "type")
    return Array.from(
      split(refs, (e) => key(e.kind, e.type, e.dimension)),
      ([id, indices]) => {
        const p = JSON.parse(id);
        return targetGroup(
          p[1],
          indices,
          "targets",
          `${category(p[0])} · ${p[2]}`,
        );
      },
    );
  return refs;
}
function chunkRoots(q) {
  const result = [];
  for (const c of buildChunks().values()) {
    if (q.dimension !== "All" && c.dimension !== q.dimension) continue;
    if (
      q.search &&
      !match(`${c.x}, ${c.z} ${c.dimension}`, q.search) &&
      !c.refs.some((i) => filterEntry(entries[i], { ...q, category: "All" }))
    )
      continue;
    result.push(chunkNode(c));
  }
  return result;
}
function getCpu(scope) {
  if (mode === "cpu") return data;
  return scope === "server"
    ? data.diagnostics?.cpu
    : list(data.diagnostics?.background?.threads).find(
        (t) => String(t.id) === scope,
      )?.cpu;
}
function overviewTasks(scope) {
  if (mode === "cpu")
    return list(data.methods).map((m) => ({
      owner: m.owner,
      method: m.type + "#" + m.name,
      samples: m.self,
      threadName: data.thread,
      methodId: m.id,
    }));
  if (scope === "server") return list(data.diagnostics?.tasks);
  const all = [];
  for (const worker of list(data.diagnostics?.background?.threads))
    for (const task of list(worker.tasks)) all.push(task);
  return all;
}
function cpuTask(task, total) {
  const name = task.method || task.owner,
    scope = task.threadId ? String(task.threadId) : "server";
  return node(name, num(task.samples), "sample", {
    count: num(task.samples),
    total,
    context: task.threadId
      ? `${task.threadName} (#${task.threadId})`
      : task.owner || "",
    task,
    detail: () => [
      ["Method", task.method || "Mod total"],
      ["Mod ID", task.owner],
      [
        "Thread",
        task.threadId
          ? `${task.threadName} (#${task.threadId})`
          : getCpu("server")?.thread || data.thread || "Not recorded",
      ],
      ["Java execution samples", task.samples],
      ["Total samples in this view", total],
      [
        "Share of listed samples (%)",
        total > 0 ? (100 * num(task.samples)) / total : 0,
      ],
      [
        "Measurement",
        "CPU samples show which code was running when a sample was taken, rather than its elapsed time or CPU usage.",
      ],
    ],
    links: () => {
      const cpu = getCpu(scope);
      return list(cpu?.methods)
        .filter(
          (m) =>
            task.method?.replace("Event callback · ", "") ===
            m.type + "#" + m.name,
        )
        .map((m) => ({
          view: "paths",
          scope,
          group: "methods",
          search: m.type + "#" + m.name,
          label: "Inspect captured call paths",
        }));
    },
  });
}
function cpuRoots(q) {
  const all = overviewTasks(q.scope),
    total = all.reduce((s, t) => s + num(t.samples), 0);
  const tasks = all.filter(
    (t) =>
      !q.search ||
      match(
        `${t.owner} ${t.method} ${t.threadName || ""} ${t.threadId || ""}`,
        q.search,
      ),
  );
  if (q.group !== "mod") return tasks.map((t) => cpuTask(t, total));
  const groups = new Map();
  for (const task of tasks) {
    if (!groups.has(task.owner)) groups.set(task.owner, []);
    groups.get(task.owner).push(task);
  }
  return Array.from(groups, ([name, children]) =>
    node(
      name,
      children.reduce((s, t) => s + num(t.samples), 0),
      "sample",
      {
        count: children.reduce((s, t) => s + num(t.samples), 0),
        total,
        context: "Mod",
        build: () => children.map((t) => cpuTask(t, total)),
      },
    ),
  );
}
function callbackNode(e) {
  return node(e.method, micro(e.nanos), "time", {
    count: num(e.calls),
    context: e.owner,
    callback: e,
    detail: () => [
      ["Handler", e.method],
      ["Mod ID", e.owner],
      ["Event", e.event],
      ["Registration ID", e.handlerId || "Not recorded"],
      ["Handler calls", e.calls],
      ["Total measured ms", num(e.nanos) / 1e6],
      [
        "Average ms/call",
        num(e.calls) > 0 ? num(e.nanos) / 1e6 / num(e.calls) : null,
      ],
      ["Longest call in ms", num(e.maxNanos) / 1e6],
      ["Measured µs/t", micro(e.nanos)],
      [
        "Measurement",
        "Includes work called by this handler, which may also be timed elsewhere.",
      ],
    ],
  });
}
function eventRoots(q) {
  const all = list(data.diagnostics?.callbacks).filter(
    (e) =>
      !q.search ||
      match(`${e.owner} ${e.method} ${e.event} ${e.handlerId || ""}`, q.search),
  );
  if (q.group !== "mod") return all.map(callbackNode);
  const groups = new Map();
  for (const e of all) {
    if (!groups.has(e.owner)) groups.set(e.owner, []);
    groups.get(e.owner).push(e);
  }
  return Array.from(groups, ([name, values]) =>
    node(name, micro(values.reduce((s, e) => s + num(e.nanos), 0)), "time", {
      count: values.reduce((s, e) => s + num(e.calls), 0),
      context: "Combined handler time",
      build: () => values.map(callbackNode),
      detail: () => [
        ["Mod ID", name],
        ["Saved handlers", values.length],
        [
          "Aggregation",
          "Adding handler times can count the same work twice. The tick time breakdown accounts for this overlap.",
        ],
      ],
    }),
  );
}
function limitDetails(cpu) {
  const l = cpu?.limits;
  if (!l) return [];
  return [
    ["Effective call-path budget MiB", num(l.budget) / 1048576],
    ["Configured ceiling MiB", num(l.configured) / 1048576],
    ["Resource mode", l.mode],
    ["Maximum retained frames", l.depth],
    ["Omitted path observations", l.omittedPaths],
    ["Omitted relationship observations", l.omittedEdges],
    ["Stacks shortened by LagSense", l.shortenedStacks],
    ["Stacks shortened by the JVM", l.jvmTruncatedStacks],
  ];
}
function methodDetail(cpu, m, counts) {
  return [
    ["Method", m.type + "#" + m.name + (m.descriptor || "")],
    ["Mod ID", m.owner],
    ["Thread", cpu.thread || "Not recorded"],
    ["Direct Java samples", counts.self],
    ["In-stack Java samples", counts.inclusive],
    ["Total saved stack samples", cpu.treeSamples],
    ...limitDetails(cpu),
    [
      "Measurement",
      "In-stack samples include calls made by this method, so they cannot be read as execution times.",
    ],
  ];
}
function pathNode(cpu, n, hierarchy) {
  const m = activeMethods.get(num(hierarchy ? n.method : n.id));
  if (!m) return null;
  const counts = hierarchy ? n : m;
  return node(
    m.type + "#" + m.name,
    num(hierarchy ? counts.inclusive : counts.self),
    "sample",
    {
      count: num(hierarchy ? counts.inclusive : counts.self),
      total: num(cpu.treeSamples),
      context: m.owner,
      detail: () => methodDetail(cpu, m, counts),
      links: () => {
        const links = [];
        for (const e of list(cpu.edges)) {
          const id =
            num(e.callee) === num(m.id)
              ? num(e.caller)
              : num(e.caller) === num(m.id)
                ? num(e.callee)
                : null;
          if (id == null) continue;
          const other = activeMethods.get(id);
          if (other)
            links.push({
              view: "paths",
              scope: config.scope,
              group: "methods",
              search: other.type + "#" + other.name,
              label: `${num(e.callee) === num(m.id) ? "Caller" : "Calls"}: ${other.type}#${other.name} · ${e.samples} samples`,
            });
        }
        return links;
      },
      build: hierarchy
        ? () =>
            list(cpuChildren.get(num(n.id)))
              .map((child) => pathNode(cpu, child, true))
              .filter(Boolean)
        : null,
    },
  );
}
function pathRoots(q) {
  activeCpu = getCpu(q.scope);
  if (!activeCpu) return [];
  activeMethods = new Map(list(activeCpu.methods).map((m) => [num(m.id), m]));
  cpuChildren = new Map();
  for (const n of list(activeCpu.nodes)) {
    if (!cpuChildren.has(num(n.parent))) cpuChildren.set(num(n.parent), []);
    cpuChildren.get(num(n.parent)).push(n);
  }
  if (q.group === "methods")
    return list(activeCpu.methods)
      .filter(
        (m) =>
          !q.search ||
          match(
            `${m.owner} ${m.type}#${m.name}${m.descriptor || ""}`,
            q.search,
          ),
      )
      .map((m) => pathNode(activeCpu, m, false));
  let relevant = null;
  if (q.search) {
    relevant = new Set();
    for (let i = list(activeCpu.nodes).length - 1; i >= 0; i--) {
      const n = activeCpu.nodes[i],
        m = activeMethods.get(num(n.method));
      if (
        m &&
        (match(`${m.owner} ${m.type}#${m.name}`, q.search) ||
          relevant.has(num(n.id)))
      ) {
        relevant.add(num(n.id));
        relevant.add(num(n.parent));
      }
    }
  }
  function build(n) {
    const row = pathNode(activeCpu, n, true);
    if (row && relevant) {
      row.build = () =>
        list(cpuChildren.get(num(n.id)))
          .filter((c) => relevant.has(num(c.id)))
          .map(build)
          .filter(Boolean);
    }
    return row;
  }
  return list(cpuChildren.get(0))
    .filter((n) => !relevant || relevant.has(num(n.id)))
    .map(build)
    .filter(Boolean);
}
// Comparison source entries remain canonical. Derived groups hold only source-index lists.
let beforeEntries = [],
  afterEntries = [],
  beforeIds = new Set(),
  afterIds = new Set();
function identity(e) {
  if (e.kind === "Entity")
    return e.entityId ? key("uuid", e.entityId) : "unknown";
  if (
    located(e) &&
    (e.kind === "Block" ||
      String(e.kind).startsWith("Random ") ||
      String(e.kind).startsWith("Scheduled "))
  )
    return key(e.dimension, e.x, e.y, e.z);
  return "unknown";
}
function stats(refs, side) {
  const source = side ? afterEntries : beforeEntries;
  let nanos = 0,
    calls = 0;
  const ids = new Set();
  let known = true;
  for (const i of refs) {
    const e = source[i];
    nanos += num(e.nanos);
    calls += num(e.calls);
    const id = identity(e);
    if (id === "unknown") known = false;
    ids.add(key(e.kind, e.type, id));
  }
  return {
    nanos,
    calls,
    records: refs.length,
    targets: known ? ids.size : null,
  };
}
function cmpValue(a, b) {
  return (
    micro(b.nanos, num(data.afterTicks)) - micro(a.nanos, num(data.beforeTicks))
  );
}
function comparisonGroup(name, before, after, extra = {}, build) {
  const a = stats(before, false),
    b = stats(after, true),
    earlier = micro(a.nanos, num(data.beforeTicks)),
    later = micro(b.nanos, num(data.afterTicks));
  return node(name, later - earlier, "delta", {
    before: before.length ? earlier : null,
    after: after.length ? later : null,
    count: a.records + b.records,
    context: extra.context || "",
    beforeRefs: before,
    afterRefs: after,
    cmp: true,
    ...extra,
    detail: () => [
      ["Earlier µs/t", before.length ? earlier : null],
      ["Later µs/t", after.length ? later : null],
      ["Change µs/t", later - earlier],
      [
        "Change (%)",
        before.length && after.length && earlier > 0
          ? 100 * (later / earlier - 1)
          : null,
      ],
      ["Earlier measured records", a.records],
      ["Later measured records", b.records],
      ["Earlier identified instances", a.targets],
      ["Later identified instances", b.targets],
      [
        "Observation",
        extra.status ||
          "A result missing from one capture does not mean the object was removed.",
      ],
    ],
    build: build ? () => build(before, after) : null,
  });
}
function splitSides(before, after, by) {
  const groups = new Map();
  for (const [source, refs, side] of [
    [beforeEntries, before, 0],
    [afterEntries, after, 1],
  ])
    for (const i of refs) {
      const id = by(source[i]);
      if (!groups.has(id)) groups.set(id, [[], []]);
      groups.get(id)[side].push(i);
    }
  return groups;
}
function contributionGroup(refs, later) {
  const source = later ? afterEntries : beforeEntries,
    ticks = num(later ? data.afterTicks : data.beforeTicks);
  return node(
    later ? "Later contributions" : "Earlier contributions",
    refs.reduce((s, i) => s + micro(source[i].nanos, ticks), 0),
    "time",
    {
      count: refs.length,
      context: later ? "Later capture" : "Earlier capture",
      build: () =>
        refs.map((i) => {
          const e = source[i];
          return node(e.type, micro(e.nanos, ticks), "time", {
            count: num(e.calls),
            context: position(e),
            tp: tpEntry(e),
            detail: () => [
              ["Capture", later ? "Later" : "Earlier"],
              ["Registry ID", e.type],
              ["Category", e.kind],
              ["Position", position(e)],
              ["UUID", e.entityId || "Not recorded"],
              ["Measured nanoseconds", e.nanos],
              ["Captured updates", e.calls],
              ["Captured ticks", ticks],
              ["Measured µs/t", micro(e.nanos, ticks)],
            ],
          });
        }),
    },
  );
}
function pairStatus(id, b, a, scope) {
  if (id === "unknown") return "Matching unavailable";
  if (b.length && a.length)
    return id.includes('"uuid"') ? "UUID matched" : "Same recorded location";
  const e = b.length ? beforeEntries[b[0]] : afterEntries[a[0]];
  if (
    (scope === "chunk" || scope === "dimension") &&
    e.entityId &&
    beforeIds.has(e.entityId) &&
    afterIds.has(e.entityId)
  )
    return `${b.length ? "Earlier" : "Later"} ${scope === "chunk" ? "chunk" : "dimension"} only · UUID matched`;
  if (data.incomplete)
    return b.length ? "Not measured later" : "Not measured earlier";
  return b.length ? "Removed from capture data" : "Added to capture data";
}
function pairs(before, after, scope) {
  const groups = splitSides(before, after, identity),
    unknown = groups.get("unknown");
  return Array.from(groups, ([id, [b, a]]) => {
    const e = a.length ? afterEntries[a[0]] : beforeEntries[b[0]];
    let status = pairStatus(id, b, a, scope);
    if (id !== "unknown" && b.length && !a.length && unknown?.[1].length)
      status = "Later identity unavailable";
    if (id !== "unknown" && a.length && !b.length && unknown?.[0].length)
      status = "Earlier identity unavailable";
    const tp = id !== "unknown" ? tpEntry(e) : null;
    return comparisonGroup(
      e.type,
      b,
      a,
      { context: status, status, tp },
      (b, a) => [
        ...(b.length ? [contributionGroup(b, false)] : []),
        ...(a.length ? [contributionGroup(a, true)] : []),
      ],
    );
  });
}
function typeGroups(before, after, scope) {
  return Array.from(
    splitSides(before, after, (e) => key(e.kind, e.type)),
    ([id, [b, a]]) => {
      const [kind, type] = JSON.parse(id);
      return comparisonGroup(type, b, a, { context: category(kind) }, (b, a) =>
        pairs(b, a, scope),
      );
    },
  );
}
function comparisonRoots(q) {
  if (!data.details) {
    const source =
      q.view === "changes-chunks" ? list(data.chunks) : list(data.types);
    return source
      .filter(
        (e) =>
          (q.dimension === "All" || e.dimension === q.dimension) &&
          (!q.search ||
            match(
              `${e.type} ${e.kind} ${e.dimension} ${e.x} ${e.z}`,
              q.search,
            )),
      )
      .map((e) =>
        node(
          q.view === "changes-chunks" ? `Chunk ${e.x}, ${e.z}` : e.type,
          num(e.afterMicros) - num(e.beforeMicros),
          "delta",
          {
            before: num(e.beforeMicros),
            after: num(e.afterMicros),
            count: num(e.beforeTargets) + num(e.afterTargets),
            context: e.dimension,
            tp:
              q.view === "changes-chunks"
                ? tpChunk({
                    dimension: e.dimension,
                    x: num(e.x),
                    z: num(e.z),
                    refs: [],
                    located: true,
                  })
                : null,
            detail: () => [
              ["Earlier µs/t", e.beforeMicros],
              ["Later µs/t", e.afterMicros],
              [
                "Individual details",
                "This older comparison has no saved individual results.",
              ],
            ],
          },
        ),
      );
  }
  const b = [],
    a = [];
  for (let i = 0; i < beforeEntries.length; i++)
    if (filterEntry(beforeEntries[i], q)) b.push(i);
  for (let i = 0; i < afterEntries.length; i++)
    if (filterEntry(afterEntries[i], q)) a.push(i);
  if (q.view === "changes-types")
    return typeGroups(b, a, q.dimension === "All" ? "type" : "dimension");
  return Array.from(
    splitSides(b, a, (e) =>
      located(e)
        ? key(e.dimension, Math.floor(num(e.x) / 16), Math.floor(num(e.z) / 16))
        : key("Location unavailable", null, null),
    ),
    ([id, [b, a]]) => {
      const [dimension, x, z] = JSON.parse(id);
      const candidates = a.length
        ? a.map((i) => afterEntries[i])
        : b.map((i) => beforeEntries[i]);
      const hotspot = candidates.find((e) => tpEntry(e));
      const tp = hotspot
        ? tpEntry(hotspot)
        : x == null
          ? null
          : tpChunk({ dimension, x, z, refs: [], located: true });
      return comparisonGroup(
        x == null ? "Location unavailable" : `Chunk ${x}, ${z}`,
        b,
        a,
        { context: dimension, tp },
        (b, a) =>
          Array.from(
            splitSides(b, a, (e) => e.kind),
            ([kind, [b, a]]) =>
              comparisonGroup(
                category(kind),
                b,
                a,
                { context: dimension },
                (b, a) => typeGroups(b, a, "chunk"),
              ),
          ),
      );
    },
  );
}
function passesDirection(n) {
  return (
    n.metric !== "delta" ||
    config.direction === "all" ||
    (config.direction === "higher" ? n.value > 0 : n.value < 0)
  );
}
function flattenPage(q) {
  const expanded = new Set(q.expanded || []),
    pageSize = Math.max(1, Math.min(200, num(q.pageSize) || 100)),
    wanted = Math.max(0, num(q.page)) * pageSize,
    output = [];
  for (const id of registry.keys()) if (id.startsWith("t")) registry.delete(id);
  const present = (n, depth) => {
    if (typeof n === "number") n = target(n);
    return {
      id: n.id,
      name: n.name,
      value: n.value,
      metric: n.metric,
      count: n.count,
      total: n.total,
      context: n.context || "",
      before: n.before,
      after: n.after,
      expandable: !!(n.build || n.children?.length),
      expanded: expanded.has(n.id),
      depth,
      chunk: n.chunk,
      tp: n.tp,
    };
  };
  let total = 0;
  if (!expanded.size) {
    const source =
      q.direction === "all" ? roots : roots.filter(passesDirection);
    total = source.length;
    for (let i = wanted; i < Math.min(source.length, wanted + pageSize); i++)
      output.push(present(source[i], 0));
  } else {
    const stack = [{ rows: roots, index: 0, depth: 0 }];
    while (stack.length) {
      const frame = stack[stack.length - 1];
      if (frame.index >= frame.rows.length) {
        stack.pop();
        continue;
      }
      const n = frame.rows[frame.index++];
      if (!passesDirection(n)) continue;
      if (total >= wanted && output.length < pageSize)
        output.push(present(n, frame.depth));
      total++;
      if (typeof n !== "number" && expanded.has(n.id))
        stack.push({ rows: children(n), index: 0, depth: frame.depth + 1 });
    }
  }
  return {
    rows: output,
    total,
    page: Math.min(
      Math.max(0, num(q.page)),
      Math.max(0, Math.ceil(total / pageSize) - 1),
    ),
    pageSize,
  };
}
function emptyMessage(q) {
  if (q.view === "events") {
    const state = data.diagnostics?.callbackState;
    return state === "disabled"
      ? "Event timing was off for this capture."
      : state === "unavailable"
        ? "Event timing was unavailable."
        : "No matching event handlers.";
  }
  if (q.view === "cpu" && q.scope === "background") {
    const bg = data.diagnostics?.background;
    return !bg
      ? "This capture has no saved background samples."
      : bg.state === "disabled"
        ? "CPU sampling was off for this capture."
        : bg.state === "pending"
          ? "CPU samples were still being processed when this report was saved."
          : bg.state === "unavailable"
            ? "Background CPU analysis was unavailable."
            : "No matching background Java execution samples.";
  }
  if (q.view === "paths") {
    const cpu = getCpu(q.scope);
    return !cpu || cpu.state === "legacy"
      ? "This capture has no saved call paths."
      : cpu.state === "disabled"
        ? "CPU sampling was off for this capture."
        : cpu.state === "pending"
          ? "Call paths were still being processed when this report was saved."
          : cpu.state === "unavailable"
            ? "Call-path analysis was unavailable."
            : "No matching call paths.";
  }
  return "No matching results.";
}
function query(q) {
  q = {
    search: "",
    dimension: "All",
    category: "All",
    group: "instances",
    sort: "cost",
    scope: "server",
    direction: "all",
    page: 0,
    pageSize: 100,
    ...q,
  };
  q.search = q.search.trim().toLowerCase();
  config = q;
  const signature = key(
    q.view,
    q.scope,
    q.search,
    q.dimension,
    q.category,
    q.group,
    q.sort,
  );
  if (signature !== queryKey) {
    queryKey = signature;
    registry = new Map();
    serial = 0;
    roots = ordered(
      q.view === "targets"
        ? targetRoots(q)
        : q.view === "chunks" || q.view === "map"
          ? chunkRoots(q)
          : q.view === "cpu"
            ? cpuRoots(q)
            : q.view === "events"
              ? eventRoots(q)
              : q.view === "paths"
                ? pathRoots(q)
                : q.view.startsWith("changes-")
                  ? comparisonRoots(q)
                  : [],
      q.sort,
    );
  }
  const result = flattenPage(q);
  return { ...result, empty: emptyMessage(q), view: q.view };
}
function overview() {
  if (mode === "comparison")
    return {
      kind: mode,
      beforeAt: data.beforeAt,
      afterAt: data.afterAt,
      beforeTicks: data.beforeTicks,
      afterTicks: data.afterTicks,
      beforeMspt: data.beforeMspt,
      afterMspt: data.afterMspt,
      beforeId: data.beforeId,
      afterId: data.afterId,
      incomplete: data.incomplete,
      presentationLimited: data.presentationLimited,
      hasDetails: !!data.details,
      limits: [data.details?.earlierLimit, data.details?.laterLimit].filter(
        Boolean,
      ),
    };
  if (mode === "cpu")
    return {
      kind: mode,
      thread: data.thread,
      samples: data.samples,
      retained: data.treeSamples,
      limited: data.limited,
      state: data.state,
    };
  if (mode === "frames") return { kind: mode, ...data };
  const diag = data.diagnostics || {};
  const top = [];
  for (let i = 0; i < entries.length; i++) {
    top.push(i);
    top.sort((a, b) => num(entries[b].nanos) - num(entries[a].nanos));
    if (top.length > 5) top.pop();
  }
  const allChunks = Array.from(buildChunks().values());
  const hottest = allChunks
    .slice()
    .sort((a, b) => b.nanos - a.nanos)
    .slice(0, 5);
  return {
    kind: mode,
    id: data.id,
    capturedAt: data.capturedAt,
    ticks: tickCount,
    mspt: data.mspt,
    tps: data.tps,
    admin: data.admin !== false,
    targets: entries.length,
    chunkTargets: mode === "chunk" ? data.targets : null,
    dimensions: Array.from(new Set(entries.map((e) => e.dimension))).length,
    chunkCount: allChunks.length,
    truncated: !!data.truncated,
    notes: list(diag.notes),
    trigger: diag.trigger,
    coverage: diag.coverage,
    callbackState: diag.callbackState,
    events: list(diag.callbacks).length,
    serverSamples: diag.cpu?.samples,
    serverLimited: diag.cpu?.limited,
    serverState: diag.cpu?.state,
    backgroundSamples: diag.background?.samples,
    backgroundLimited: diag.background?.limited,
    backgroundReason: diag.background?.reason,
    backgroundState: diag.background?.state,
    unlocated: entries.filter((e) => !located(e)).length,
    topTargets: top.map((i) => ({
      name: entries[i].type,
      value: micro(entries[i].nanos),
      position: position(entries[i]),
    })),
    topChunks: hottest.map((c) => ({
      name: `Chunk ${c.x}, ${c.z}`,
      dimension: c.dimension,
      value: micro(c.nanos),
    })),
    chunkPartial:
      mode === "chunk" &&
      (num(data.offset) > 0 || entries.length < num(data.targets)),
    bookkeepingNanos: diag.bookkeepingNanos,
  };
}
function metadata() {
  const source =
    mode === "comparison"
      ? [
          ...new Set(
            [...beforeEntries, ...afterEntries].map((e) => e.dimension),
          ),
        ]
      : Array.from(new Set(entries.map((e) => e.dimension)));
  if (mode === "comparison" && !data.details)
    for (const e of [...list(data.types), ...list(data.chunks)])
      if (!source.includes(e.dimension)) source.push(e.dimension);
  const kinds = Array.from(
    new Set(
      (mode === "comparison"
        ? [...beforeEntries, ...afterEntries]
        : entries
      ).map((e) => e.kind),
    ),
  ).sort();
  const workers = list(data.diagnostics?.background?.threads).map((t) => ({
    value: String(t.id),
    label: `${t.name} (#${t.id})`,
    samples: t.cpu?.samples,
  }));
  return {
    kind: mode,
    dimensions: source.filter(Boolean).sort(),
    categories: kinds,
    scopes:
      mode === "cpu"
        ? [{ value: "capture", label: data.thread || "Captured thread" }]
        : [
            { value: "server", label: "Server thread" },
            { value: "background", label: "Background threads" },
          ],
    pathScopes:
      mode === "cpu"
        ? [{ value: "capture", label: data.thread || "Captured thread" }]
        : [
            {
              value: "server",
              label: data.diagnostics?.cpu?.thread || "Server thread",
            },
            ...workers,
          ],
    summary: overview(),
  };
}
function mapTiles(q) {
  const width = Math.max(1, Math.min(2400, num(q.width))),
    height = Math.max(1, Math.min(1000, num(q.height))),
    scale = Math.max(0.000001, Math.min(96, num(q.scale) || 12)),
    bins = new Map();
  let total = 0,
    visible = 0;
  for (const c of buildChunks().values()) {
    if (c.dimension !== q.dimension || !c.located) continue;
    total++;
    const x = (c.x - q.x) * scale + width / 2,
      z = (c.z - q.z) * scale + height / 2;
    if (x + scale < 0 || z + scale < 0 || x >= width || z >= height) continue;
    visible++;
    const bx = scale < 3 ? Math.floor(x / 3) * 3 : x,
      bz = scale < 3 ? Math.floor(z / 3) * 3 : z,
      id = key(bx, bz);
    const old = bins.get(id),
      value = micro(c.nanos);
    if (!old)
      bins.set(id, { x: bx, z: bz, size: Math.max(3, scale), value, count: 1 });
    else {
      old.count++;
      old.value = Math.max(old.value, value);
    }
  }
  return {
    tiles: Array.from(bins.values()),
    visible,
    total,
    width,
    height,
    scale,
  };
}
function mapBounds(dimension) {
  let minX = Infinity,
    maxX = -Infinity,
    minZ = Infinity,
    maxZ = -Infinity;
  for (const c of buildChunks().values())
    if (c.dimension === dimension && c.located) {
      minX = Math.min(minX, c.x);
      maxX = Math.max(maxX, c.x);
      minZ = Math.min(minZ, c.z);
      maxZ = Math.max(maxZ, c.z);
    }
  return minX === Infinity ? null : { minX, maxX, minZ, maxZ };
}
function mapPick(q) {
  const c = buildChunks().get(
    key(q.dimension, Math.floor(num(q.x)), Math.floor(num(q.z))),
  );
  return c
    ? {
        name: `Chunk ${c.x}, ${c.z}`,
        dimension: c.dimension,
        value: micro(c.nanos),
        targets: c.targets,
        x: c.x,
        z: c.z,
      }
    : null;
}
onmessage = function (event) {
  const { cmd, token } = event.data;
  try {
    let result;
    if (cmd === "init") {
      documentData = JSON.parse(event.data.raw);
      mode = documentData.kind;
      data = documentData.data;
      documentData = null;
      if (mode === "profile") entries = list(data.entries);
      else if (mode === "chunk")
        entries = list(data.hotspots).map((h) => h.entry);
      tickCount = num(data.ticks);
      if (mode === "comparison") {
        beforeEntries = list(data.details?.earlier);
        afterEntries = list(data.details?.later);
        beforeIds = new Set(
          beforeEntries.map((e) => e.entityId).filter(Boolean),
        );
        afterIds = new Set(afterEntries.map((e) => e.entityId).filter(Boolean));
      }
      result = metadata();
    } else if (cmd === "query") result = query(event.data.query);
    else if (cmd === "detail") {
      const n =
        registry.get(event.data.id) ||
        (String(event.data.id).startsWith("t") &&
        Number.isInteger(Number(String(event.data.id).slice(1))) &&
        entries[Number(String(event.data.id).slice(1))]
          ? target(Number(String(event.data.id).slice(1)))
          : null);
      result = n
        ? {
            id: n.id,
            title: n.name,
            fields: n.detail
              ? n.detail()
              : [
                  ["Name", n.name],
                  ["Value", n.value],
                ],
            links: n.links ? n.links() : [],
            tp: n.tp,
          }
        : null;
    } else if (cmd === "map") result = mapTiles(event.data.query);
    else if (cmd === "bounds") result = mapBounds(event.data.dimension);
    else if (cmd === "pick") result = mapPick(event.data.query);
    postMessage({ cmd, token, result });
  } catch (error) {
    postMessage({ cmd, token, error: String(error?.message || error) });
  }
};
