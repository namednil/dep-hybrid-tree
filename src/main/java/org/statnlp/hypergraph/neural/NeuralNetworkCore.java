package org.statnlp.hypergraph.neural;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import org.statnlp.hypergraph.Network;
import org.statnlp.hypergraph.NetworkConfig;
import org.statnlp.hypergraph.NetworkConfig.ModelStatus;
import org.statnlp.hypergraph.neural.util.LuaFunctionHelper;

import gnu.trove.iterator.TIntIterator;
import gnu.trove.list.TIntList;
import gnu.trove.list.array.TIntArrayList;
import gnu.trove.map.TIntIntMap;
import gnu.trove.map.TIntObjectMap;
import gnu.trove.map.hash.TIntIntHashMap;
import gnu.trove.set.TIntSet;
import gnu.trove.set.hash.TIntHashSet;
//import th4j.Tensor.DoubleTensor;

public abstract class NeuralNetworkCore extends AbstractNeuralNetwork implements Cloneable {
	
	private static final long serialVersionUID = -2638896619016178432L;

	protected HashMap<String,Object> config;
	
	protected transient boolean isTraining;
	
	public transient boolean optimizeNeural;
	
	/**
	 * Neural network input to index (id)
	 * If you are using batch training, do not directly use this to obtain input id.
	 * Use the method # {@link #getNNInputID()}
	 */
	protected transient Map<Object, Integer> nnInput2Id;
	
	/**
	 * Save the mapping from instance id to neural network input id.
	 */
	protected transient TIntObjectMap<TIntList> instId2NNInputId;
	
	/**
	 * Dynamically save the batch instance id in batch training.
	 */
	protected transient TIntIntMap dynamicNNInputId2BatchInputId;
	
	protected boolean continuousFeatureValue = false;
	
	protected String nnModelFile = null;
	
	/**
	 * GPU ID required, as for loading model as well. By default is less than 0 means CPU mode.
	 */
	protected int gpuid = -1;
	
	public NeuralNetworkCore(int numLabels, int gpuid) {
		super(numLabels);
		config = new HashMap<>();
		optimizeNeural = NetworkConfig.OPTIMIZE_NEURAL;
		this.gpuid = gpuid;
		config.put("optimizeNeural", optimizeNeural);
		config.put("numLabels", numLabels);
		config.put("gpuid", gpuid);
	}
	
	@Override
	public void initialize() {
		
	}

	protected void prepareContinuousFeatureValue() {
		//should be overrided
	}
	
	/**
	 * Calculate the input position in the output/countOuput matrix position
	 * @return
	 */
	public abstract int hyperEdgeInput2OutputRowIndex(Object edgeInput);
	
	public int getNNInputID(Object nnInput) {
		if (NetworkConfig.USE_BATCH_TRAINING && isTraining) {
			return this.dynamicNNInputId2BatchInputId.get(this.nnInput2Id.get(nnInput));
		} else {
			return this.nnInput2Id.get(nnInput);
		}
	}
	
	public int getNNInputSize() {
		if (NetworkConfig.USE_BATCH_TRAINING && isTraining) {
			return this.dynamicNNInputId2BatchInputId.size();
		} else {
			return this.nnInput2Id.size();
		}
	}
	
	/**
	 * Neural network's forward
	 */
	@Override
	public void forward(TIntSet batchInstIds) {
		int x = 3 / 0;
	}
	
	@Override
	public double getScore(Network network, int parent_k, int children_k_index) {
		double val = 0.0;
		NeuralIO io = getHyperEdgeInputOutput(network, parent_k, children_k_index);
		if (io != null) {
			Object edgeInput = io.getInput();
			int outputLabel = io.getOutput();
			int idx = this.hyperEdgeInput2OutputRowIndex(edgeInput) * this.numLabels + outputLabel;
			val = output[idx];
		}
		return val;
	}
	
	/**
	 * Neural network's backpropagation
	 */
	@Override
	public void backward() {
		
	}
	
	@Override
	public void update(double count, Network network, int parent_k, int children_k_index) {
		NeuralIO io = getHyperEdgeInputOutput(network, parent_k, children_k_index);
		if (io != null) {
			Object edgeInput = io.getInput();
			int outputLabel = io.getOutput();
			int idx = this.hyperEdgeInput2OutputRowIndex(edgeInput) * this.numLabels + outputLabel;
			synchronized (countOutput) {
				//TODO: alternatively, create #threads of countOutput array.
				//Then aggregate them together.
				countOutput[idx] -= count;
			}
		}
	}
	
	public void resetCountOutput() {
		Arrays.fill(countOutput, 0.0);
	}
	
	/**
	 * Save the model by calling the specific function in Torch
	 * @param func : the function in torch
	 * @param prefix : model prefix
	 */
	public void save(String func, String prefix) {
		LuaFunctionHelper.execLuaFunction(this.L, func, new Object[]{prefix}, new Class[]{});
	}
	
	/**
	 * Save the trained model, implement the "save_model" method in torch
	 * @param prefix
	 */
	public void save(String prefix) {
		
	}
	
	/**
	 * Load a trained model, using the specific function in Torch
	 * @param func: the specific function for loading model
	 * @param prefix: model prefix.
	 */
	public void load(String func, String prefix, int gpuid) {
		LuaFunctionHelper.execLuaFunction(this.L, func, new Object[]{prefix, gpuid}, new Class[]{});
	}
	
	/**
	 * Load a model from disk, implement the "load_model" method in torch
	 * @param prefix
	 */
	public void load() {
		this.load("load_model", this.nnModelFile, this.gpuid);
	}
	
	@Override
	public void closeProvider() {
		this.cleanUp();
	}
	
	/**
	 * Clean up resources, currently, we clean up the resource after decoding
	 */
	public void cleanUp() {
		L.close();
	}
	
	@Override
	protected NeuralNetworkCore clone(){
		NeuralNetworkCore c = null;
		try {
			c = (NeuralNetworkCore) super.clone();
			c.nnInput2Id = null;
			c.params = this.params;
		} catch (CloneNotSupportedException e) {
			e.printStackTrace();
		}
		return c;
	}
	
	public NeuralNetworkCore setModelFile(String nnModelFile) {
		this.nnModelFile = nnModelFile;
		return this;
	}

	private void writeObject(ObjectOutputStream out) throws IOException{
		out.writeObject(this.config);
		out.writeBoolean(this.continuousFeatureValue);
		out.writeInt(this.netId);
		out.writeInt(this.numLabels);
		out.writeDouble(this.scale);
		out.writeObject(this.nnModelFile);
		out.writeInt(this.gpuid);
		this.save(this.nnModelFile);
	}
	
	@SuppressWarnings("unchecked")
	private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException{
		this.config = (HashMap<String, Object>) in.readObject();
		this.continuousFeatureValue = in.readBoolean();
		this.netId = in.readInt();
		this.numLabels = in.readInt();
		this.scale = in.readDouble();
		this.nnModelFile = (String) in.readObject();
		this.gpuid = in.readInt();
		this.config.put("nnModelFile", this.nnModelFile);
		this.configureJNLua();
		this.load();
	}
	
}


