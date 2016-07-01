// Generated by the protocol buffer compiler.  DO NOT EDIT!
// source: google/bigtable/admin/cluster/v1/bigtable_cluster_data.proto

package com.google.bigtable.admin.cluster.v1;

public interface ZoneOrBuilder extends
    // @@protoc_insertion_point(interface_extends:google.bigtable.admin.cluster.v1.Zone)
    com.google.protobuf.MessageOrBuilder {

  /**
   * <code>optional string name = 1;</code>
   *
   * <pre>
   * A permanent unique identifier for the zone.
   * Values are of the form projects/&lt;project&gt;/zones/[a-z][-a-z0-9]*
   * </pre>
   */
  java.lang.String getName();
  /**
   * <code>optional string name = 1;</code>
   *
   * <pre>
   * A permanent unique identifier for the zone.
   * Values are of the form projects/&lt;project&gt;/zones/[a-z][-a-z0-9]*
   * </pre>
   */
  com.google.protobuf.ByteString
      getNameBytes();

  /**
   * <code>optional string display_name = 2;</code>
   *
   * <pre>
   * The name of this zone as it appears in UIs.
   * </pre>
   */
  java.lang.String getDisplayName();
  /**
   * <code>optional string display_name = 2;</code>
   *
   * <pre>
   * The name of this zone as it appears in UIs.
   * </pre>
   */
  com.google.protobuf.ByteString
      getDisplayNameBytes();

  /**
   * <code>optional .google.bigtable.admin.cluster.v1.Zone.Status status = 3;</code>
   *
   * <pre>
   * The current state of this zone.
   * </pre>
   */
  int getStatusValue();
  /**
   * <code>optional .google.bigtable.admin.cluster.v1.Zone.Status status = 3;</code>
   *
   * <pre>
   * The current state of this zone.
   * </pre>
   */
  com.google.bigtable.admin.cluster.v1.Zone.Status getStatus();
}