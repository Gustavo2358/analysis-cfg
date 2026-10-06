package io.github.gustavo2358.analysis.dependencies.source;

import java.util.*;
import java.util.function.Function;

/** Versioned non-executable source evidence. References form an AND/OR certificate, not AIR control. */
public record QualifiedSourceDependencies(String schema, String version, String producer, Document source, List<AirCorrelation> air, List<UnitEvidence> units) {
        public QualifiedSourceDependencies {
            text(schema);
            text(version);
            text(producer);
            Objects.requireNonNull(source);
            air=List.copyOf(air);
            units=List.copyOf(units);
            require(schema.equals("qualified-source-dependencies") && Set.of("1.0.0","1.1.0","1.2.0","1.3.0","1.4.0","1.5.0","1.6.0").contains(version), "unsupported source contract"); require(Set.of("1.4.0","1.5.0","1.6.0").contains(version)||units.stream().noneMatch(u->u.nominalValues().filter(n->n.facts().authority().equals("NOMINAL_TEXT_SOURCE_V4")).isPresent()),"source tables require version 1.4.0"); require(Set.of("1.3.0","1.4.0","1.5.0","1.6.0").contains(version)||units.stream().noneMatch(u->u.nominalValues().filter(n->n.facts().authority().equals("NOMINAL_TEXT_SOURCE_V3")).isPresent()),"source expressions require version 1.3.0"); one(air); unique(units, UnitEvidence::unit);
            require(Set.of("1.5.0","1.6.0").contains(version)||units.stream().allMatch(u->u.derivations().stream().noneMatch(d->d.authority().equals("ALTERNATE_ENTRY"))),"alternate roots require version 1.5.0");
            require(version.equals("1.6.0")||units.stream().allMatch(u->u.nodes().stream().allMatch(n->n.support().conditions().isEmpty())&&u.events().stream().noneMatch(e->e.origin().equals("LINK_PGMIDERR"))&&u.proofs().stream().noneMatch(p->Set.of("cics-pgmiderr-condition-event","cics-condition-registration").contains(p.rule()))),"condition state requires version 1.6.0");
            require(!version.equals("1.0.0")||units.stream().allMatch(u->u.nativeFiles().isEmpty()&&u.proofs().stream().noneMatch(p->p.kind().equals("CONTROL_POSSIBILITY"))),"source possibility requires version 1.1.0");
            require(Set.of("1.2.0","1.3.0","1.4.0","1.5.0","1.6.0").contains(version)||units.stream().allMatch(u->u.nodes().stream().noneMatch(n->n.support().cause().equals("SOURCE_REENTRY_UNDEFINED"))),"source reentry requires version 1.2.0");
        }
    public record Document(String schema, String version, String sha256) {
        public Document {
            text(schema);
            text(version);
            text(sha256);
            require(schema.equals("cobol-semantic-product") || schema.equals("cobol-semantic-compilation"), "source schema"); digest(sha256);
        }
    }
    public record AirCorrelation(String publication, String sha256) {
        public AirCorrelation {
            text(publication);
            text(sha256);
            digest(sha256);
        }
    }
    public record UnitId(String compilationUnitId, List<Integer> structuralPath, String canonicalProgramName) {
        public UnitId {
            text(compilationUnitId);
            structuralPath=List.copyOf(structuralPath);
            text(canonicalProgramName);
            require(!structuralPath.isEmpty() && structuralPath.stream().allMatch(n -> n >= 0), "unit path");
        }
    }
    public record StatementId(UnitId unit, String handle) {
        public StatementId {
            Objects.requireNonNull(unit);
            text(handle);
        }
    }
    public record OperandId(StatementId statement, String handle) {
        public OperandId {
            Objects.requireNonNull(statement);
            text(handle);
        }
    }
    public record Location(String file, int startLine, int startColumn, int endLine, int endColumn) {
        public Location {
            text(file);
            require(startLine >= 1 && endLine >= startLine && startColumn >= 0 && endColumn >= 0, "source location");
        }
    }
    public record Include(String includingFile, String requestedName, String includedFile, int includeLine) {
        public Include {
            text(includingFile);
            text(requestedName);
            text(includedFile);
            require(includeLine >= 1, "include line");
        }
    }
    public record Provenance(Location expanded, Location original, List<Include> includeChain, boolean exact) {
        public Provenance {
            Objects.requireNonNull(expanded);
            Objects.requireNonNull(original);
            includeChain=List.copyOf(includeChain);
        }
    }
    public record Statement(StatementId id, Provenance provenance) {
        public Statement {
            Objects.requireNonNull(id);
            Objects.requireNonNull(provenance);
        }
    }
    public record Operand(OperandId id, Provenance provenance) {
        public Operand {
            Objects.requireNonNull(id);
            Objects.requireNonNull(provenance);
        }
    }
    public record Value(String logicalDomain, String value, int logicalExtent) {
        public Value {
            text(logicalDomain);
            Objects.requireNonNull(value);
            require(logicalDomain.equals("TEXT") && logicalExtent >= 0 && value.codePointCount(0,value.length()) == logicalExtent, "qualified text value");
        }
    }
    public record Occurrence(StatementId id, String technology, String command, String namespace, String nameProfile, String targetKind, List<Operand> operands, List<Value> values, boolean valueRemainder, List<String> qualifications) {
        public Occurrence {
            Objects.requireNonNull(id);
            text(technology);
            text(command);
            text(namespace);
            text(nameProfile);
            text(targetKind);
            operands=List.copyOf(operands);
            values=List.copyOf(values);
            qualifications=List.copyOf(qualifications);
            one(operands); one(values); require(Set.of("LITERAL","COMPUTED","UNAVAILABLE").contains(targetKind), "target kind");
            require(values.isEmpty() || targetKind.equals("LITERAL") && operands.size()==1, "literal value authority");
            require(valueRemainder == values.isEmpty(), "value remainder");
            require(operands.stream().allMatch(o -> o.id().statement().equals(id)), "operand owner");
            require(technology.equals("COBOL") ? command.equals("CALL") && namespace.equals("PROGRAM") : technology.equals("CICS") && Set.of("PROGRAM","FILE").contains(namespace), "dependency family");
        }
    }
    public record ConditionState(String condition,StatementId registration,boolean uncertain,List<String> proofs) {
        public ConditionState{text(condition);Objects.requireNonNull(registration);proofs=List.copyOf(proofs);require(Set.of("PGMIDERR","ERROR").contains(condition)&&!proofs.isEmpty(),"supported condition state");}
    }
    public record Support(String kind, List<String> target, List<StatementId> activation, String cause,List<ConditionState> conditions) {
        public Support(String kind,List<String> target,List<StatementId> activation,String cause){this(kind,target,activation,cause,List.of());}
        public Support {
            conditions=List.copyOf(conditions);unique(conditions,ConditionState::condition);
            text(kind);
            target=List.copyOf(target);
            activation=List.copyOf(activation);
            text(cause);
            one(target); one(activation); require(Set.of("ENTRY_UNKNOWN","ACTIVE","CANCELED","DEACTIVATED","CANCELED_UNKNOWN","UNKNOWN").contains(kind), "support kind");
            require(Set.of("NONE","RESET_HISTORY_UNAVAILABLE","RESET_WITHOUT_CANCELED_EVIDENCE","CALL_EFFECT_UNAVAILABLE","HANDLER_OPERATION_UNAVAILABLE","SOURCE_REENTRY_UNDEFINED").contains(cause), "support cause");
            require(Set.of("ACTIVE","CANCELED","DEACTIVATED").contains(kind) == !target.isEmpty() && target.size()==activation.size(), "support correlation");
            require(kind.equals("UNKNOWN") != cause.equals("NONE"), "unknown cause");
        }
    }
    public record Node(String id, String context, String location, Support support) {
        public Node {
            text(id);
            text(context);
            text(location);
            Objects.requireNonNull(support);
        }
    }
    public record Registration(StatementId statement, Provenance statementOrigin, Provenance operandOrigin) {
        public Registration {
            Objects.requireNonNull(statement);
            Objects.requireNonNull(statementOrigin);
            Objects.requireNonNull(operandOrigin);
        }
    }
    public record Target(String id, String form, List<StatementId> entry, List<Registration> registrations, List<Operand> programOperands, List<Value> programValues) {
        public Target {
            text(id);
            text(form);
            entry=List.copyOf(entry);
            registrations=List.copyOf(registrations);
            programOperands=List.copyOf(programOperands);
            programValues=List.copyOf(programValues);
            one(entry); one(programOperands); one(programValues); require(Set.of("LABEL_LOCAL","LABEL_UNRESOLVED","PROGRAM_LITERAL","PROGRAM_DATA","PROGRAM_UNRESOLVED").contains(form), "target form"); require(!registrations.isEmpty(), "registration authority");
        }
    }
    public record Proof(String id, String kind, String rule, Provenance provenance, List<String> dependencies) {
        public Proof {
            text(id);
            text(kind);
            text(rule);
            Objects.requireNonNull(provenance);
            dependencies=List.copyOf(dependencies);
            require(Set.of("LOCAL_GRAMMAR","RESOLVED_TARGET","EXPANDED_INCLUDE","INPUT_REGION_ISOLATION","PARTIAL_UNKNOWN","CONTROL_POSSIBILITY").contains(kind), "proof kind");
        }
    }
    public record Guard(String id, String event, String kind) {
        public Guard {
            text(id);
            text(event);
            text(kind);
            require(Set.of("CONDITION_RAISED","DEFAULT_DISPOSITION_APPLIES").contains(kind), "runtime guard");
        }
    }
    public record Event(String id, StatementId statement, String origin, String disposition, String eligibility, String scope, String runtimeIdentity, List<String> guards, List<String> proofs) {
        public Event {
            text(id);
            Objects.requireNonNull(statement);
            text(origin);
            text(disposition);
            text(eligibility);
            text(scope);
            text(runtimeIdentity);
            guards=List.copyOf(guards);
            proofs=List.copyOf(proofs);
            require(Set.of("EXPLICIT_ABEND","XCTL_PGMIDERR","LINK_PGMIDERR").contains(origin), "event origin");
            require(disposition.equals("TASK_ABEND") && scope.equals("CURRENT_EXECUTION_LOGICAL_LEVEL") && runtimeIdentity.equals("UNAVAILABLE"), "event scope");
            require(Set.of("HANDLER_ELIGIBLE","HANDLERS_BYPASSED").contains(eligibility) && !proofs.isEmpty(), "event authority");
        }
    }
    public record Selection(String id, String event, String source, List<String> target, List<Support> stateOnEntry, List<String> localEntry, List<String> guards, List<String> proofs, boolean unknownLocalRemainder, boolean localInactivePossible, boolean outerLevelRemainder, boolean bypassed) {
        public Selection {
            text(id);
            text(event);
            text(source);
            target=List.copyOf(target);
            stateOnEntry=List.copyOf(stateOnEntry);
            localEntry=List.copyOf(localEntry);
            guards=List.copyOf(guards);
            proofs=List.copyOf(proofs);
            one(target); one(stateOnEntry); one(localEntry); require(target.size()==stateOnEntry.size() && (!target.isEmpty() || localEntry.isEmpty()), "selection shape");
        }
    }
    public record Derivation(String id, List<String> source, String destination, List<String> callerPremise, String authority, List<String> proofs, List<String> selection) {
        public Derivation {
            text(id);
            source=List.copyOf(source);
            text(destination);
            callerPremise=List.copyOf(callerPremise);
            text(authority);
            proofs=List.copyOf(proofs);
            selection=List.copyOf(selection);
            one(source); one(callerPremise); one(selection); require(!proofs.isEmpty() && (callerPremise.isEmpty() || !source.isEmpty()), "derivation authority");
        }
    }
    public record Frontier(String source, String authority, String reference, List<String> proofs) {
        public Frontier {
            text(source);
            text(authority);
            text(reference);
            proofs=List.copyOf(proofs);
        }
    }
    public record NativeFileName(String declaration,UnitId owner,String logicalFile,String rawValue,List<Provenance> declarationOrigins) {
        public NativeFileName {text(declaration);Objects.requireNonNull(owner);text(logicalFile);text(rawValue);declarationOrigins=List.copyOf(declarationOrigins);require(!declarationOrigins.isEmpty(),"native file declaration provenance");}
    }
    /** Source identity is (statement, ordinal); never an invented AIR operation. */
    public record NativeFileUse(StatementId statement,int ordinal,String controlLocation,String command,boolean local,List<NativeFileName> names,Provenance provenance,List<String> qualifications,List<String> gaps) {
        public NativeFileUse {
            Objects.requireNonNull(statement);text(controlLocation);require(ordinal>=0,"native file ordinal");
            require(Set.of("OPEN","READ","WRITE","REWRITE","DELETE_RECORD","START","CLOSE","RELEASE","RETURN","SORT","MERGE").contains(command),"native file command");
            names=List.copyOf(names);one(names);Objects.requireNonNull(provenance);qualifications=List.copyOf(qualifications);gaps=List.copyOf(gaps);
            require(!local||names.isEmpty(),"local sort work is not an external file");
        }
    }
    public record UnitEvidence(UnitId unit, boolean controlAvailable, List<Statement> statements, List<Occurrence> occurrences, List<Target> targets, List<Node> nodes, List<Derivation> derivations, List<Selection> selections, List<Event> events, List<Guard> guards, List<Proof> proofs, List<Frontier> frontiers, Optional<NominalValueEvidence> nominalValues,List<NativeFileUse> nativeFiles) {
        public UnitEvidence(UnitId unit,boolean controlAvailable,List<Statement> statements,List<Occurrence> occurrences,List<Target> targets,List<Node> nodes,List<Derivation> derivations,List<Selection> selections,List<Event> events,List<Guard> guards,List<Proof> proofs,List<Frontier> frontiers,Optional<NominalValueEvidence> nominalValues) {
            this(unit,controlAvailable,statements,occurrences,targets,nodes,derivations,selections,events,guards,proofs,frontiers,nominalValues,List.of());
        }
        public UnitEvidence(UnitId unit,boolean controlAvailable,List<Statement> statements,List<Occurrence> occurrences,List<Target> targets,List<Node> nodes,List<Derivation> derivations,List<Selection> selections,List<Event> events,List<Guard> guards,List<Proof> proofs,List<Frontier> frontiers) {
            this(unit,controlAvailable,statements,occurrences,targets,nodes,derivations,selections,events,guards,proofs,frontiers,Optional.empty());
        }
        public UnitEvidence {
            nativeFiles=List.copyOf(nativeFiles);
            if(!nativeFiles.isEmpty()) {
            var sourceStatements=unique(statements,Statement::id);var sourceNodes=unique(nodes,Node::id);var uses=new HashSet<String>();
            for(var f:nativeFiles) {
                require(f.statement().unit().equals(unit)&&sourceStatements.containsKey(f.statement()),"native file statement");
                require(uses.add(f.statement().handle()+"/"+f.ordinal()),"duplicate native file use");refs(f.qualifications(),sourceNodes);
                for(var q:f.qualifications())require(sourceNodes.get(q).location().equals(f.controlLocation()),"native file qualification owner");
                require(new HashSet<>(f.qualifications()).equals(nodes.stream().filter(n->n.location().equals(f.controlLocation())).map(Node::id).collect(java.util.stream.Collectors.toSet())),"complete native file alternatives");
                require(controlAvailable||f.qualifications().isEmpty(),"native file control availability");
            }
            }
            Objects.requireNonNull(nominalValues);
            if(nominalValues.isPresent())nominalValues.get().validate(statements,occurrences,nodes,derivations);
            Objects.requireNonNull(unit);
            statements=List.copyOf(statements);
            occurrences=List.copyOf(occurrences);
            targets=List.copyOf(targets);
            nodes=List.copyOf(nodes);
            derivations=List.copyOf(derivations);
            selections=List.copyOf(selections);
            events=List.copyOf(events);
            guards=List.copyOf(guards);
            proofs=List.copyOf(proofs);
            frontiers=List.copyOf(frontiers);
            validate(unit,controlAvailable,statements,occurrences,targets,nodes,derivations,selections,events,guards,proofs,frontiers);
        }
    }
    private static void validate(UnitId unit,boolean available,List<Statement> statements,List<Occurrence> occurrences,
            List<Target> targets,List<Node> nodes,List<Derivation> derivations,List<Selection> selections,
            List<Event> events,List<Guard> guards,List<Proof> proofs,List<Frontier> frontiers) {
        var ss=unique(statements,Statement::id); var os=unique(occurrences,Occurrence::id);
        var ts=unique(targets,Target::id);var ns=unique(nodes,Node::id);var ds=unique(derivations,Derivation::id);
        var sels=unique(selections,Selection::id);var es=unique(events,Event::id);var gs=unique(guards,Guard::id);var ps=unique(proofs,Proof::id);
        for(var s:statements) require(s.id().unit().equals(unit),"statement unit");
        for(var t:targets) {
            refs(t.entry(),ss);var registrations=new HashSet<StatementId>();
            for(var r:t.registrations()) {require(ss.containsKey(r.statement()) && registrations.add(r.statement()),"registration identity");
                require(ss.get(r.statement()).provenance().equals(r.statementOrigin()),"registration provenance");}
            for(var o:t.programOperands())require(registrations.contains(o.id().statement()),"program operand owner");
            require(t.programValues().isEmpty() || t.form().equals("PROGRAM_LITERAL") && !t.programOperands().isEmpty(),"program literal authority");
        }
        for(var p:proofs)refs(p.dependencies(),ps);
        // Reject ungrounded proof cycles using a linear dependency worklist.
        var proofReady=new HashSet<String>();var proofWait=new HashMap<String,List<String>>();var counts=new HashMap<String,Integer>();var queue=new ArrayDeque<String>();
        for(var p:proofs){counts.put(p.id(),p.dependencies().size());if(p.dependencies().isEmpty())queue.add(p.id());for(var ref:p.dependencies())proofWait.computeIfAbsent(ref,k->new ArrayList<>()).add(p.id());}
        while(!queue.isEmpty()){var id=queue.removeFirst();proofReady.add(id);for(var dest:proofWait.getOrDefault(id,List.of()))if(counts.merge(dest,-1,Integer::sum)==0)queue.add(dest);}
        require(proofReady.size()==proofs.size(),"proof cycle");
        for(var n:nodes)checkSupport(n.support(),ts,ss,ps);
        for(var g:guards)require(es.containsKey(g.event()),"guard event reference");
        for(var e:events){require(ss.containsKey(e.statement()),"event statement");refs(e.guards(),gs);refs(e.proofs(),ps);
            for(var ref:e.guards())require(gs.get(ref).event().equals(e.id()),"guard owner");
            var kinds=e.guards().stream().map(ref->gs.get(ref).kind()).toList();
            require(e.origin().equals("EXPLICIT_ABEND")?kinds.isEmpty():kinds.equals(List.of("CONDITION_RAISED","DEFAULT_DISPOSITION_APPLIES")) && e.eligibility().equals("HANDLER_ELIGIBLE"),"origin guard correlation");
            var rule=e.origin().equals("EXPLICIT_ABEND")?"cics-explicit-abend-event":e.origin().equals("LINK_PGMIDERR")?"cics-link-pgmiderr-default-abend-event":"cics-xctl-pgmiderr-default-abend-event";
            require(e.proofs().stream().anyMatch(ref->ps.get(ref).kind().equals("LOCAL_GRAMMAR") && ps.get(ref).rule().equals(rule)),"event proof authority");
        }
        for(var s:selections){require(es.containsKey(s.event()) && ns.containsKey(s.source()),"selection reference");refs(s.target(),ts);refs(s.localEntry(),ns);refs(s.guards(),gs);refs(s.proofs(),ps);
            var e=es.get(s.event());var source=ns.get(s.source());
            require(source.location().equals(e.statement().handle()) && e.statement().unit().equals(unit),"selection source event");
            require(s.guards().equals(e.guards()) && s.proofs().equals(e.proofs()),"selection event qualification");
            require(s.bypassed()==e.eligibility().equals("HANDLERS_BYPASSED"),"bypass qualification");
            if(!s.target().isEmpty()) {
                require(!s.bypassed() && source.support().kind().equals("ACTIVE") && source.support().target().equals(s.target()),"selected ACTIVE target");
                var entry=s.stateOnEntry().getFirst();checkSupport(entry,ts,ss,ps);
                require(entry.kind().equals("DEACTIVATED") && entry.target().equals(s.target()) && entry.activation().equals(source.support().activation()),"deactivated entry support");
                if(!s.localEntry().isEmpty()) {
                    var n=ns.get(s.localEntry().getFirst());var t=ts.get(s.target().getFirst());
                    require(n.support().equals(entry) && t.form().equals("LABEL_LOCAL") && t.entry().size()==1 && n.location().equals(t.entry().getFirst().handle()),"local entry correlation");
                }
            } else require(s.localEntry().isEmpty(),"unselected local entry");
            if(s.bypassed())require(s.target().isEmpty() && !s.unknownLocalRemainder() && !s.localInactivePossible() && !s.outerLevelRemainder(),"bypassed remainder");
        }
        // Validate the finite certificate (not handler transfer semantics). Multiple
        // incoming derivations remain OR; each source/caller pair remains AND.
        var pending=new byte[derivations.size()];var heads=new int[nodes.size()];Arrays.fill(heads,-1);
        var sourceNext=new int[derivations.size()];var callerNext=new int[derivations.size()];
        var destinations=new int[derivations.size()];var ready=new int[nodes.size()];var reached=new BitSet(nodes.size());int read=0,write=0,ordinal=0;
        for(var d:derivations){refs(d.source(),ns);refs(d.callerPremise(),ns);refs(d.selection(),sels);refs(d.proofs(),ps);require(ns.containsKey(d.destination()),"derivation destination");
            if(d.source().isEmpty())require(d.callerPremise().isEmpty() && d.selection().isEmpty() && Set.of("PRIMARY_ENTRY","ALTERNATE_ENTRY").contains(d.authority()),"ordinary root authority");
            if(d.source().isEmpty()&&d.authority().equals("ALTERNATE_ENTRY")) {
                require(d.proofs().stream().map(ps::get).anyMatch(p->p.kind().equals("LOCAL_GRAMMAR")&&p.rule().equals("alternate-entry-start")),"alternate root requires entry proof");
                require(ns.get(d.destination()).context().startsWith("ENTRY/"),"alternate root context");
            }
            if(!d.selection().isEmpty()) {var s=sels.get(d.selection().getFirst());require(d.source().equals(List.of(s.source())) && d.callerPremise().isEmpty() && s.localEntry().equals(List.of(d.destination())) && d.proofs().equals(s.proofs()),"selection derivation correlation");}
            int destination=ns.ordinal(d.destination());destinations[ordinal]=destination;
            int source=d.source().isEmpty()?-1:ns.ordinal(d.source().getFirst());
            int caller=d.callerPremise().isEmpty()?-1:ns.ordinal(d.callerPremise().getFirst());
            if(source>=0){sourceNext[ordinal]=heads[source];heads[source]=Math.multiplyExact(ordinal,2);pending[ordinal]++;}
            if(caller>=0&&caller!=source){callerNext[ordinal]=heads[caller];heads[caller]=Math.addExact(Math.multiplyExact(ordinal,2),1);pending[ordinal]++;}
            if(pending[ordinal]==0&&!reached.get(destination)){reached.set(destination);ready[write++]=destination;}
            ordinal++;
        }
        while(read<write)for(int link=heads[ready[read++]];link>=0;) {
            int index=link>>>1;int next=(link&1)==0?sourceNext[index]:callerNext[index];
            if(--pending[index]==0){int destination=destinations[index];if(!reached.get(destination)){reached.set(destination);ready[write++]=destination;}}
            link=next;
        }
        require(reached.cardinality()==nodes.size(),"ungrounded qualification node");
        var selected=new HashSet<String>();for(var d:derivations)selected.addAll(d.selection());
        for(var s:selections)if(!s.localEntry().isEmpty())require(selected.contains(s.id()),"missing selection derivation");
        var observed=new HashSet<String>();occurrences.forEach(o->observed.add(o.id().handle()));
        var atLocation=new HashMap<String,Set<String>>();for(var n:nodes)if(observed.contains(n.location()))atLocation.computeIfAbsent(n.location(),k->new HashSet<>()).add(n.id());
        for(var o:occurrences){require(ss.containsKey(o.id()),"occurrence identity");refs(o.qualifications(),ns);require(new HashSet<>(o.qualifications()).equals(atLocation.getOrDefault(o.id().handle(),Set.of())),"complete occurrence alternatives");
            for(var ref:o.qualifications())require(ns.get(ref).location().equals(o.id().handle()),"occurrence node correlation");}
        for(var f:frontiers){require(ns.containsKey(f.source()),"frontier source");refs(f.proofs(),ps);}
        require(available || nodes.isEmpty() && derivations.isEmpty() && selections.isEmpty() && events.isEmpty() && proofs.isEmpty() && frontiers.isEmpty(),"unavailable control has no authority");
    }
    private static void checkSupport(Support s,Map<String,Target> targets,Map<StatementId,Statement> statements,Map<String,Proof> proofs) {
        for(var condition:s.conditions()) {
            require(statements.containsKey(condition.registration()),"condition registration reference");refs(condition.proofs(),proofs);
            require(condition.proofs().stream().map(proofs::get).anyMatch(p->p.kind().equals("LOCAL_GRAMMAR")&&p.rule().equals("cics-condition-registration")&&p.provenance().equals(statements.get(condition.registration()).provenance())),"condition registration proof/origin");
        }
        refs(s.target(),targets);refs(s.activation(),statements);
        if(!s.target().isEmpty())require(targets.get(s.target().getFirst()).registrations().stream().anyMatch(r->r.statement().equals(s.activation().getFirst())),"support activation/target");
    }
    /** Immutable chained index into the owned inventory; exact equality resolves collisions. */
    private static final class CompactIndex<K,T> extends AbstractMap<K,T> {
        private final List<T> values;private final Function<T,K> key;private final int[] heads,next;
        private final Map<Integer,Map<K,Integer>> overflow=new HashMap<>();
        CompactIndex(List<T> values,Function<T,K> key) {
            this.values=values;this.key=key;int capacity=1;while(capacity<values.size())capacity=Math.multiplyExact(capacity,2);
            heads=new int[capacity];Arrays.fill(heads,-1);next=new int[values.size()];
            for(int i=0;i<values.size();i++) {
                K id=key.apply(values.get(i));require(ordinal(id)<0,"duplicate identity");
                int bucket=bucket(id);var crowded=overflow.get(bucket);
                if(crowded!=null){crowded.put(id,i);continue;}
                int count=0;for(int at=heads[bucket];at>=0;at=next[at])count++;
                if(count>=8){crowded=new HashMap<>(64);for(int at=heads[bucket];at>=0;at=next[at])crowded.put(key.apply(values.get(at)),at);crowded.put(id,i);overflow.put(bucket,crowded);}
                else {next[i]=heads[bucket];heads[bucket]=i;}
            }
        }
        private int bucket(Object id){int hash=id.hashCode();return (hash^(hash>>>16))&(heads.length-1);}
        int ordinal(Object id){
            if(id==null)return -1;int bucket=bucket(id);
            if(!overflow.isEmpty()){var crowded=overflow.get(bucket);if(crowded!=null)return crowded.getOrDefault(id,-1);}
            for(int at=heads[bucket];at>=0;at=next[at])if(id.equals(key.apply(values.get(at))))return at;return -1;
        }
        @Override public T get(Object id){int at=ordinal(id);return at<0?null:values.get(at);}
        @Override public boolean containsKey(Object id){return ordinal(id)>=0;}
        @Override public int size(){return values.size();}
        @Override public Set<Map.Entry<K,T>> entrySet(){return new AbstractSet<>() {
            @Override public int size(){return values.size();}
            @Override public Iterator<Map.Entry<K,T>> iterator(){return new Iterator<>() {
                int at;
                public boolean hasNext(){return at<values.size();}
                public Map.Entry<K,T> next(){if(!hasNext())throw new NoSuchElementException();T value=values.get(at++);return Map.entry(key.apply(value),value);}
            };}
        };}
    }
    private static <T,K> CompactIndex<K,T> unique(List<T> xs,Function<T,K> key){return new CompactIndex<>(xs,key);}
    private static <T> void refs(List<T> refs,Map<T,?> index){var seen=new HashSet<T>();for(var r:refs)require(index.containsKey(r) && seen.add(r),"invalid/duplicate reference");}
    private static void one(List<?> xs){require(xs.size()<=1,"optional cardinality");}
    private static void text(String s){require(s!=null && !s.isBlank(),"empty identity/descriptor");}
    private static void digest(String s){require(s.matches("[0-9a-f]{64}"),"SHA-256 digest");}
    private static void require(boolean test,String message){if(!test)throw new IllegalArgumentException(message);}

}
