package org.aion4j.avm.idea.service;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiParameter;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.stream.Collectors;

@State(name="aion4j-cache", reloadable = true, storages = @com.intellij.openapi.components.Storage("aion4j-cache.xml"))
public class AvmCacheService implements PersistentStateComponent<AvmCacheService.State> {
    private static int MAX_CACHE_ENTRY = 30;

    public static class State {
        public Map<String, List<String>> methodArgs = new HashMap<>();
        public Map<String, String> deployArgs = new HashMap<>();
        public Queue<String> localContractAddresses = new ArrayDeque<>(10);
        public Queue<String> remoteContractAddresses = new ArrayDeque<>(10);
    }

    public State state;

    public State getState() {
        if(state == null)
            return new State();
        else
            return state;
    }

    public String encodeMethod(PsiMethod method) {
        StringBuilder sb = new StringBuilder();

        String name = method.getName();
        sb.append(name);
        sb.append("-");
        for (PsiParameter param : method.getParameterList().getParameters()) {
            String type = param.getType().getCanonicalText();
            sb.append(type);
            sb.append(",");
        }

        return sb.toString();
    }

    public List<String> getArgsFromCache(@NotNull PsiMethod method) {
        String encMethod = encodeMethod(method);
        return getState().methodArgs.get(encMethod);
    }

    public void updateArgsToCache(@NotNull PsiMethod method, List<String> args) {
        String encMethod = encodeMethod(method);

        if(state == null)
            state = this.getState();

        if(state.methodArgs.size() >= MAX_CACHE_ENTRY) {
            state.methodArgs.clear();
        }

        state.methodArgs.put(encMethod, args);
    }

    public String getDeployArgs(String module) {
        return getState().deployArgs.get(getModuleKeyForDeployArgs(module));
    }

    public void updateDeployArgs(String module, String deployArgs) {
        if(state == null)
            state = this.getState();

        if(deployArgs != null)
            deployArgs = deployArgs.trim();

        state.deployArgs.put(getModuleKeyForDeployArgs(module), deployArgs);
    }

    public boolean shouldNotAskDeployArgs(String module) {
        String dontAsk = getState().deployArgs.get(getModuleKeyForDeployArgsDontAsk(module));

        return dontAsk != null? Boolean.parseBoolean(dontAsk): false;
    }

    public void setShouldNotAskDeployArgs(String module, boolean flag) {
        if(state == null)
            state = this.getState();

        state.deployArgs.put(getModuleKeyForDeployArgsDontAsk(module), Boolean.toString(flag));
    }

    public Map<String, String> getAllDeployArgsWithModuleName() {
        if(getState().deployArgs == null || getState().deployArgs.isEmpty())
            return Collections.EMPTY_MAP;

        return getState().deployArgs.entrySet().stream()
                .filter(entry -> entry.getKey().endsWith("args") && !StringUtil.isEmptyOrSpaces(entry.getValue()))
                .collect(Collectors.toMap(x -> x.getKey(), x -> x.getValue()));
    }

    public List<String> getLocalContractAddresses() {
        List<String> contractAddresses = getState().localContractAddresses.parallelStream().collect(Collectors.toList());
        Collections.reverse(contractAddresses);
        return contractAddresses;
    }

    public void addLocalContractAddress(String address) {

        if(state == null)
            state = this.getState();

        if(this.state.localContractAddresses.size() > 10)
            this.state.localContractAddresses.poll();

        List<String> contractAddresses = getLocalContractAddresses();
        if(!contractAddresses.contains(address))
            this.state.localContractAddresses.add(address);
    }

    public List<String> getRemoteContractAddresses() {
        List<String> contractAddresses = getState().remoteContractAddresses.parallelStream().collect(Collectors.toList());
        Collections.reverse(contractAddresses);
        return contractAddresses;
    }

    public void addRemoteContractAddress(String address) {

        if(state == null)
            state = this.getState();

        if(this.state.remoteContractAddresses.size() > 10)
            this.state.remoteContractAddresses.poll();

        List<String> contractAddresses = getRemoteContractAddresses();
        if(!contractAddresses.contains(address))
            this.state.remoteContractAddresses.add(address);
    }

    public void loadState(State state) {
        this.state = state;
    }


    private String getModuleKeyForDeployArgs(String moduleName) {
        if(StringUtil.isEmptyOrSpaces(moduleName))
            return "args";
        else;
        return moduleName + ".args";
    }

    private String getModuleKeyForDeployArgsDontAsk(String moduleName) {
        if(StringUtil.isEmptyOrSpaces(moduleName))
            return "dontask";
        else
            return moduleName + ".dontask";
    }
}
