// Generated by the protocol buffer compiler.  DO NOT EDIT!
// source: google/bigtable/v1/bigtable_service_messages.proto

package com.google.bigtable.v1;

public interface ReadRowsRequestOrBuilder extends
    // @@protoc_insertion_point(interface_extends:google.bigtable.v1.ReadRowsRequest)
    com.google.protobuf.MessageOrBuilder {

  /**
   * <code>optional string table_name = 1;</code>
   *
   * <pre>
   * The unique name of the table from which to read.
   * </pre>
   */
  java.lang.String getTableName();
  /**
   * <code>optional string table_name = 1;</code>
   *
   * <pre>
   * The unique name of the table from which to read.
   * </pre>
   */
  com.google.protobuf.ByteString
      getTableNameBytes();

  /**
   * <code>optional bytes row_key = 2;</code>
   *
   * <pre>
   * The key of a single row from which to read.
   * </pre>
   */
  com.google.protobuf.ByteString getRowKey();

  /**
   * <code>optional .google.bigtable.v1.RowRange row_range = 3;</code>
   *
   * <pre>
   * A range of rows from which to read.
   * </pre>
   */
  com.google.bigtable.v1.RowRange getRowRange();
  /**
   * <code>optional .google.bigtable.v1.RowRange row_range = 3;</code>
   *
   * <pre>
   * A range of rows from which to read.
   * </pre>
   */
  com.google.bigtable.v1.RowRangeOrBuilder getRowRangeOrBuilder();

  /**
   * <code>optional .google.bigtable.v1.RowSet row_set = 8;</code>
   *
   * <pre>
   * A set of rows from which to read. Entries need not be in order, and will
   * be deduplicated before reading.
   * The total serialized size of the set must not exceed 1MB.
   * </pre>
   */
  com.google.bigtable.v1.RowSet getRowSet();
  /**
   * <code>optional .google.bigtable.v1.RowSet row_set = 8;</code>
   *
   * <pre>
   * A set of rows from which to read. Entries need not be in order, and will
   * be deduplicated before reading.
   * The total serialized size of the set must not exceed 1MB.
   * </pre>
   */
  com.google.bigtable.v1.RowSetOrBuilder getRowSetOrBuilder();

  /**
   * <code>optional .google.bigtable.v1.RowFilter filter = 5;</code>
   *
   * <pre>
   * The filter to apply to the contents of the specified row(s). If unset,
   * reads the entire table.
   * </pre>
   */
  boolean hasFilter();
  /**
   * <code>optional .google.bigtable.v1.RowFilter filter = 5;</code>
   *
   * <pre>
   * The filter to apply to the contents of the specified row(s). If unset,
   * reads the entire table.
   * </pre>
   */
  com.google.bigtable.v1.RowFilter getFilter();
  /**
   * <code>optional .google.bigtable.v1.RowFilter filter = 5;</code>
   *
   * <pre>
   * The filter to apply to the contents of the specified row(s). If unset,
   * reads the entire table.
   * </pre>
   */
  com.google.bigtable.v1.RowFilterOrBuilder getFilterOrBuilder();

  /**
   * <code>optional bool allow_row_interleaving = 6;</code>
   *
   * <pre>
   * By default, rows are read sequentially, producing results which are
   * guaranteed to arrive in increasing row order. Setting
   * "allow_row_interleaving" to true allows multiple rows to be interleaved in
   * the response stream, which increases throughput but breaks this guarantee,
   * and may force the client to use more memory to buffer partially-received
   * rows. Cannot be set to true when specifying "num_rows_limit".
   * </pre>
   */
  boolean getAllowRowInterleaving();

  /**
   * <code>optional int64 num_rows_limit = 7;</code>
   *
   * <pre>
   * The read will terminate after committing to N rows' worth of results. The
   * default (zero) is to return all results.
   * Note that "allow_row_interleaving" cannot be set to true when this is set.
   * </pre>
   */
  long getNumRowsLimit();

  public com.google.bigtable.v1.ReadRowsRequest.TargetCase getTargetCase();
}
