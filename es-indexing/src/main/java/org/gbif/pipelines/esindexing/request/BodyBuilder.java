package org.gbif.pipelines.esindexing.request;

import org.gbif.pipelines.esindexing.common.FileUtils;
import org.gbif.pipelines.esindexing.common.JsonHandler;
import org.gbif.pipelines.esindexing.common.SettingsType;

import java.io.UnsupportedEncodingException;
import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import org.apache.http.HttpEntity;
import org.apache.http.nio.entity.NStringEntity;

import static org.gbif.pipelines.esindexing.common.EsConstants.Action;
import static org.gbif.pipelines.esindexing.common.EsConstants.Constant;
import static org.gbif.pipelines.esindexing.common.EsConstants.Field;
import static org.gbif.pipelines.esindexing.common.EsConstants.Indexing;
import static org.gbif.pipelines.esindexing.common.EsConstants.Searching;
import static org.gbif.pipelines.esindexing.common.JsonHandler.createArrayNode;
import static org.gbif.pipelines.esindexing.common.JsonHandler.createObjectNode;
import static org.gbif.pipelines.esindexing.common.JsonHandler.writeToString;

/** Class that builds {@link HttpEntity} instances with JSON content. */
public class BodyBuilder {

  // pre-defined settings
  private static final ObjectNode INDEXING_SETTINGS = createObjectNode();
  private static final ObjectNode SEARCH_SETTINGS = createObjectNode();

  private JsonNode settings;
  private JsonNode mappings;
  private IndexAliasAction indexAliasAction;

  static {
    INDEXING_SETTINGS.put(Field.INDEX_REFRESH_INTERVAL, Indexing.REFRESH_INTERVAL);
    INDEXING_SETTINGS.put(Field.INDEX_NUMBER_SHARDS, Constant.NUMBER_SHARDS);
    INDEXING_SETTINGS.put(Field.INDEX_NUMBER_REPLICAS, Indexing.NUMBER_REPLICAS);
    INDEXING_SETTINGS.put(Field.INDEX_TRANSLOG_DURABILITY, Constant.TRANSLOG_DURABILITY);

    SEARCH_SETTINGS.put(Field.INDEX_REFRESH_INTERVAL, Searching.REFRESH_INTERVAL);
    SEARCH_SETTINGS.put(Field.INDEX_NUMBER_REPLICAS, Searching.NUMBER_REPLICAS);
  }

  private BodyBuilder() {}

  /** Creates a new {@link BodyBuilder}. */
  public static BodyBuilder newInstance() {
    return new BodyBuilder();
  }

  /** Creates a {@link HttpEntity} from a {@link String} that will become the body of the entity. */
  public static HttpEntity createBodyFromString(String body) {
    return createEntity(body);
  }

  /** Adds a {@link SettingsType} to the body. */
  public BodyBuilder withSettingsType(SettingsType settingsType) {
    Objects.requireNonNull(settingsType);
    this.settings = (settingsType == SettingsType.INDEXING) ? INDEXING_SETTINGS : SEARCH_SETTINGS;
    return this;
  }

  /** Adds a {@link java.util.Map} of settings to the body. */
  public BodyBuilder withSettingsMap(Map<String, String> settingsMap) {
    this.settings = JsonHandler.convertToJsonNode(settingsMap);
    return this;
  }

  /** Adds ES mappings in JSON format to the body. */
  public BodyBuilder withMappings(String mappings) {
    Preconditions.checkArgument(
        !Strings.isNullOrEmpty(mappings), "Mappings cannot be null or empty");
    this.mappings = JsonHandler.readTree(mappings);
    return this;
  }

  /** Adds ES mappings from a file in JSON format to the body. */
  public BodyBuilder withMappings(Path mappingsPath) {
    Objects.requireNonNull(mappingsPath, "The path of the mappings cannot be null");
    this.mappings = JsonHandler.readTree(FileUtils.loadFile(mappingsPath));
    return this;
  }

  /**
   * Adds actions to add and remove index from an alias. Note that the indexes to be removed will be
   * removed completely from the ES instance.
   *
   * @param alias alias that wil be modify. This parameter is required.
   * @param idxToAdd indexes to add to the alias.
   * @param idxToRemove indexes to remove from the alias. These indexes will be completely removed
   *     form the ES instance.
   */
  public BodyBuilder withIndexAliasAction(
      String alias, Set<String> idxToAdd, Set<String> idxToRemove) {
    this.indexAliasAction = new IndexAliasAction(alias, idxToAdd, idxToRemove);
    return this;
  }

  public HttpEntity build() {
    ObjectNode body = createObjectNode();

    // add settings
    if (Objects.nonNull(settings)) {
      body.set(Field.SETTINGS, settings);
    }

    // add mappings
    if (Objects.nonNull(mappings)) {
      body.set(Field.MAPPINGS, mappings);
    }

    // add alias actions
    if (Objects.nonNull(indexAliasAction)) {
      body.set(Field.ACTIONS, createIndexAliasActions(indexAliasAction));
    }

    // create entity and return
    return createEntity(body);
  }

  /**
   * Builds a {@link ArrayNode} with the specified JSON content to add and remove indexes from an
   * alias.
   */
  private ArrayNode createIndexAliasActions(IndexAliasAction indexAliasAction) {
    Preconditions.checkArgument(!Strings.isNullOrEmpty(indexAliasAction.alias));

    ArrayNode actions = createArrayNode();

    // remove all indixes from alias action
    if (Objects.nonNull(indexAliasAction.idxToRemove)) {
      indexAliasAction.idxToRemove.forEach(idx -> removeIndexFromAliasAction(idx, actions));
    }
    // add index action
    if (Objects.nonNull(indexAliasAction.idxToAdd)) {
      indexAliasAction.idxToAdd.forEach(
          idx -> addIndexToAliasAction(indexAliasAction.alias, idx, actions));
    }

    return actions;
  }

  private static void removeIndexFromAliasAction(String idxToRemove, ArrayNode actions) {
    // create swap node
    ObjectNode swapNode = createObjectNode();
    swapNode.put(Field.INDEX, idxToRemove);

    // add the node to the action
    ObjectNode action = createObjectNode();
    action.set(Action.REMOVE_INDEX, swapNode);
    actions.add(action);
  }

  private static void addIndexToAliasAction(String alias, String idx, ArrayNode actions) {
    // create swap node
    ObjectNode swapNode = createObjectNode();
    swapNode.put(Field.INDEX, idx);
    swapNode.put(Field.ALIAS, alias);

    // add the node to the action
    ObjectNode action = createObjectNode();
    action.set(Action.ADD, swapNode);
    actions.add(action);
  }

  private static HttpEntity createEntity(ObjectNode entityNode) {
    return createEntity(writeToString(entityNode));
  }

  private static HttpEntity createEntity(String body) {
    try {
      return new NStringEntity(body);
    } catch (UnsupportedEncodingException exc) {
      throw new IllegalStateException(exc.getMessage(), exc);
    }
  }

  private static class IndexAliasAction {

    String alias;
    Set<String> idxToAdd;
    Set<String> idxToRemove;

    IndexAliasAction(String alias, Set<String> idxToAdd, Set<String> idxToRemove) {
      this.alias = alias;
      this.idxToAdd = idxToAdd;
      this.idxToRemove = idxToRemove;
    }
  }
}
