/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.seatunnel.connectors.seatunnel.maxcompute.sink;

import org.apache.seatunnel.api.configuration.ReadonlyConfig;
import org.apache.seatunnel.api.sink.SupportMultiTableSinkWriter;
import org.apache.seatunnel.api.table.type.SeaTunnelRow;
import org.apache.seatunnel.api.table.type.SeaTunnelRowType;
import org.apache.seatunnel.common.exception.CommonErrorCodeDeprecated;
import org.apache.seatunnel.connectors.seatunnel.common.sink.AbstractSinkWriter;
import org.apache.seatunnel.connectors.seatunnel.maxcompute.exception.MaxcomputeConnectorException;
import org.apache.seatunnel.connectors.seatunnel.maxcompute.util.MaxcomputeTypeMapper;
import org.apache.seatunnel.connectors.seatunnel.maxcompute.util.MaxcomputeUtil;

import com.aliyun.odps.PartitionSpec;
import com.aliyun.odps.Table;
import com.aliyun.odps.TableSchema;
import com.aliyun.odps.data.Record;
import com.aliyun.odps.data.RecordWriter;
import com.aliyun.odps.tunnel.TableTunnel;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;

import static org.apache.seatunnel.connectors.seatunnel.maxcompute.config.MaxcomputeConfig.PARTITION_SPEC;
import static org.apache.seatunnel.connectors.seatunnel.maxcompute.config.MaxcomputeConfig.PROJECT;
import static org.apache.seatunnel.connectors.seatunnel.maxcompute.config.MaxcomputeConfig.TABLE_NAME;

@Slf4j
public class MaxcomputeWriter extends AbstractSinkWriter<SeaTunnelRow, Void>
        implements SupportMultiTableSinkWriter<Void> {
    private RecordWriter recordWriter;
    private final TableTunnel.UploadSession session;
    private final TableSchema tableSchema;
    private static final Long BLOCK_0 = 0L;
    private final SeaTunnelRowType rowType;

    public MaxcomputeWriter(ReadonlyConfig readonlyConfig, SeaTunnelRowType rowType) {
        try {
            this.rowType = rowType;
            Table table = MaxcomputeUtil.getTable(readonlyConfig);
            this.tableSchema = table.getSchema();
            TableTunnel tunnel = MaxcomputeUtil.getTableTunnel(readonlyConfig);
            if (readonlyConfig.getOptional(PARTITION_SPEC).isPresent()) {
                PartitionSpec partitionSpec = new PartitionSpec(readonlyConfig.get(PARTITION_SPEC));
                session =
                        tunnel.createUploadSession(
                                readonlyConfig.get(PROJECT),
                                readonlyConfig.get(TABLE_NAME),
                                partitionSpec);
            } else {
                session =
                        tunnel.createUploadSession(
                                readonlyConfig.get(PROJECT), readonlyConfig.get(TABLE_NAME));
            }
            this.recordWriter = session.openRecordWriter(BLOCK_0);
            log.info("open record writer success");
        } catch (Exception e) {
            throw new MaxcomputeConnectorException(
                    CommonErrorCodeDeprecated.WRITER_OPERATION_FAILED, e);
        }
    }

    @Override
    public void write(SeaTunnelRow seaTunnelRow) throws IOException {
        Record record =
                MaxcomputeTypeMapper.getMaxcomputeRowData(
                        seaTunnelRow, this.tableSchema, this.rowType);
        recordWriter.write(record);
    }

    @Override
    public void close() throws IOException {
        if (recordWriter != null) {
            recordWriter.close();
            try {
                session.commit(new Long[] {BLOCK_0});
            } catch (Exception e) {
                throw new MaxcomputeConnectorException(
                        CommonErrorCodeDeprecated.WRITER_OPERATION_FAILED, e);
            }
            recordWriter = null;
        }
    }
}
