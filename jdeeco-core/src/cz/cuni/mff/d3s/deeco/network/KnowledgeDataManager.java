package cz.cuni.mff.d3s.deeco.network;

import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import cz.cuni.mff.d3s.deeco.knowledge.ChangeSet;
import cz.cuni.mff.d3s.deeco.knowledge.KnowledgeManager;
import cz.cuni.mff.d3s.deeco.knowledge.KnowledgeManagerContainer;
import cz.cuni.mff.d3s.deeco.knowledge.KnowledgeNotFoundException;
import cz.cuni.mff.d3s.deeco.knowledge.KnowledgeUpdateException;
import cz.cuni.mff.d3s.deeco.knowledge.ValueSet;
import cz.cuni.mff.d3s.deeco.logging.Log;
import cz.cuni.mff.d3s.deeco.model.runtime.api.EnsembleDefinition;
import cz.cuni.mff.d3s.deeco.model.runtime.api.KnowledgePath;
import cz.cuni.mff.d3s.deeco.model.runtime.custom.RuntimeMetadataFactoryExt;
import cz.cuni.mff.d3s.deeco.model.runtime.meta.RuntimeMetadataFactory;
import cz.cuni.mff.d3s.deeco.scheduler.CurrentTimeProvider;


/**
 * This class represents the layer between the network and the
 * {@link KnowledgeManagerContainer}.
 * 
 * <p>
 * It is responsible for preparing knowledge for publication on the network and
 * also for processing the knowledge coming from the network and updating the
 * {@link KnowledgeManagerContainer} (see {@link #prepareKnowledgeData()} and
 * {@link #receive(List)}). A single "snapshot" of a knowledge repository (i.e.,
 * a belief) is represented by the {@link KnowledgeData} class, which contains
 * both the knowledge values and all the metadata necessary for knowledge
 * propagation over network.
 * </p>
 * <p>
 * It uses the interfaces {@link KnowledgeDataSender} and
 * {@link KnowledgeDataReceiver} for interfacing with the network layer.
 * </p>
 *  
 * @author Jaroslav Keznikl <keznikl@d3s.mff.cuni.cz>
 * 
 * @see KnowledgeManagerContainer
 * @see KnowledgeData
 * @see KnowledgeDataSender
 * @see KnowledgeDataReceiver 
 */
public class KnowledgeDataManager implements KnowledgeDataReceiver,
KnowledgeDataPublisher {
	
	// this rssi corresponds to roughly 20m distance
	public static final double RSSI_20m = 5.52e-8; 
	// this rssi corresponds to roughly 10m distance
	public static final double RSSI_10m = 3.12e-7;
	
	/** Global version counter for all outgoing local knowledge. */
	protected long localVersion;	
	/** Service for convenient time retrieval. */
	protected final CurrentTimeProvider timeProvider;
	/** The identification of the node on which the manager is running. */
	protected final String host;	
	/** The local knowledge container that is connected by this object to the network. */
	protected final KnowledgeManagerContainer kmContainer;	
	/** Object used for sending {@link KnowledgeData} over the network. */
	protected final KnowledgeDataSender knowledgeDataSender;	
	/** Stores received KnowledgeMetaData for replicas (received ValueSet is deleted) */
	protected final Map<KnowledgeManager, KnowledgeMetaData> replicaMetadata;
	/** Empty knowledge path enabling convenient query for all knowledge */  
	private final List<KnowledgePath> emptyPath;
	/** List of ensemble definitions whose boundary conditions should be considered. */
	private final List<EnsembleDefinition> ensembleDefinitions;

	
	/**
	 * Creates an initialized instance.
	 * 
	 * @param kmContainer	the local knowledge container that should be connected by this manager to the network
	 * @param knowledgeDataSender	object used for sending {@link KnowledgeData} over the network
	 * @param host	identification of the node on which this manager is running
	 * @param timeProvider	service providing current time
	 */	
	public KnowledgeDataManager(
			KnowledgeManagerContainer kmContainer,
			KnowledgeDataSender knowledgeDataSender,
			List<EnsembleDefinition> ensembleDefinitions,
			String host,
			CurrentTimeProvider timeProvider) {
		this.host = host;		
		this.timeProvider = timeProvider;
		this.kmContainer = kmContainer;
		this.knowledgeDataSender = knowledgeDataSender;
		this.ensembleDefinitions = ensembleDefinitions;
		this.localVersion = 0;
		this.replicaMetadata = new HashMap<>();
		
		RuntimeMetadataFactory factory = RuntimeMetadataFactoryExt.eINSTANCE;
		KnowledgePath empty = factory.createKnowledgePath();
		emptyPath = new LinkedList<>();
		emptyPath.add(empty);
	}
	
	@Override
	public void publish() {
		List<? extends KnowledgeData> data = prepareKnowledgeData();
		
		logPublish(data);
		
		//TODO
		if (!data.isEmpty()) {
			
//			// broadcast each kd individually to minimize the message size and
//			// thus reduce network collisions.
//			for (KnowledgeData kd: data)
//				knowledgeDataSender.broadcastKnowledgeData(Arrays.asList(kd));
			knowledgeDataSender.broadcastKnowledgeData(data);
			localVersion++;
		}
	}

	@Override
	public void receive(List<? extends KnowledgeData> knowledgeData) {
		if (knowledgeData == null) 
			Log.w("KnowledgeDataManager.receive: Received null KnowledgeData.");
		
		logReceive(knowledgeData);
		KnowledgeMetaData md;
		for (KnowledgeData kd : knowledgeData) {
			md = kd.getMetaData();
			if (kmContainer.hasLocal(md.componentId)) {
				Log.i("KnowledgeDataManager.receive: Dropping KnowledgeData for local component " + md.componentId);
				continue;
			} 
			KnowledgeManager km = kmContainer.createReplica(md.componentId);						
			
			try {
				KnowledgeMetaData currentMD = replicaMetadata.get(km);
				// accept only fresh knowledge data (drop if we have already newer value)
				boolean haveOlder = (currentMD == null) || (currentMD.versionId < md.versionId); 
				if (haveOlder) {							
					km.update(toChangeSet(kd.getKnowledge()));
					md.hopCount++;
					//	store the metadata without the knowledge values
					replicaMetadata.put(km, md);
					
					Log.d(String.format("Receive (%d) at %s got %sv%d after %dms and %d hops\n", 
							timeProvider.getCurrentTime(), 
							host, 
							md.componentId, 
							md.versionId,
							timeProvider.getCurrentTime() - md.createdAt,
							md.hopCount));
				} 
			} catch (KnowledgeUpdateException e) {
				Log.w(String
						.format("KnowledgeDataManager.receive: Could not update replica of %s.",
								md.componentId), e);
			}
		}
		
	}

	List<? extends KnowledgeData> prepareKnowledgeData() {
		List<KnowledgeData> result = new LinkedList<>();
		for (KnowledgeManager km : kmContainer.getLocals()) {
			try {
				result.add(new KnowledgeData(
						km.get(emptyPath), 
						new KnowledgeMetaData(km.getId(), localVersion, host, timeProvider.getCurrentTime(), 0)));
				
			} catch (KnowledgeNotFoundException e) {
				Log.e("prepareKnowledgeData error", e);
			}
		}
		
		for (KnowledgeManager km : kmContainer.getReplicas()) {
			try {
				
				KnowledgeMetaData kmd = replicaMetadata.get(km);
				KnowledgeManager nodeKm = getNodeKnowledge();
								
				if (!satisfiesGossipCondition(kmd)) { 
					Log.d(String.format("Gossip condition failed (%d) at %s for %sv%d from %s with rssi %g\n", 
							timeProvider.getCurrentTime(), host, kmd.componentId, kmd.versionId, kmd.sender, kmd.rssi));
					continue;
				}
				
				Log.d(String.format("Gossip condition succeeded (%d) at %s for %sv%d from %s with rssi %g\n", 
						timeProvider.getCurrentTime(), host, kmd.componentId, kmd.versionId, kmd.sender, kmd.rssi));
				
				KnowledgeMetaData kmdCopy = kmd.clone();
				kmdCopy.sender = host;
				KnowledgeData kd = new KnowledgeData(km.get(emptyPath), kmdCopy);

				if (!isInSomeBoundary(kd, nodeKm)) {
					Log.d(String.format("Boundary failed (%d) at %s for %sv%d\n", 
							timeProvider.getCurrentTime(), host, kmd.componentId, kmd.versionId));
					continue;
				} 
					
				result.add(kd);
					
				
			} catch (KnowledgeNotFoundException e) {
				Log.e("prepareKnowledgeData error", e);
			}
		}
		
		return result;
	}

	private boolean satisfiesGossipCondition(KnowledgeMetaData kmd) {
		// rssi < 0 means received from IP
		// the signal came from more than 20m
		return kmd.rssi < 0 || kmd.rssi <= RSSI_20m;		
	}

	private boolean isInSomeBoundary(KnowledgeData data, KnowledgeManager sender) {
		boolean isInSomeBoundary = false;
		for (EnsembleDefinition ens: ensembleDefinitions) {
			// null boundary condition counts as a satisfied one
			if (ens.getCommunicationBoundary() == null 
					|| ens.getCommunicationBoundary().eval(data, sender)) {
				isInSomeBoundary = true;
				break;
			}
		}
		return isInSomeBoundary;
	}

	private KnowledgeManager getNodeKnowledge() {
		// FIXME: in the future, we need to unify the knowledge of all the local KMs.
		return kmContainer.getLocals().iterator().next();
	}

	private ChangeSet toChangeSet(ValueSet valueSet) {
		if (valueSet != null) {
			ChangeSet result = new ChangeSet();
			for (KnowledgePath kp : valueSet.getKnowledgePaths())
				result.setValue(kp, valueSet.getValue(kp));
			return result;
		} else {
			return null;
		}
	}
	
	private void logPublish(List<? extends KnowledgeData> data) {
		StringBuilder sb = new StringBuilder();
		for (KnowledgeData kd: data) {
			sb.append(kd.getMetaData().componentId + "v" + kd.getMetaData().versionId);
			sb.append(", ");			
		}		
		Log.d(String.format("Publish (%d) at %s, sending [%s]\n", 
				timeProvider.getCurrentTime(), host, sb.toString()));
	}
	
	private void logReceive(List<? extends KnowledgeData> knowledgeData) {
		StringBuilder sb = new StringBuilder();
		for (KnowledgeData kd: knowledgeData) {
			sb.append(kd.getMetaData().componentId + "v" + kd.getMetaData().versionId + "<-" + kd.getMetaData().sender);
			sb.append(", ");			
		}
		
		Log.d(String.format("Receive (%d) at %s, received [%s]\n", 
				timeProvider.getCurrentTime(), host, sb.toString()));
	}
}
