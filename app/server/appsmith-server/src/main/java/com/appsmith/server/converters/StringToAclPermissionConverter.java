package com.appsmith.server.converters;

import com.appsmith.server.acl.AclPermission;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.convert.ReadingConverter;

/**
 * PocketFM CE fork: Fault-tolerant converter for AclPermission enum.
 * If MongoDB contains an EE-only permission constant that is not defined in CE's
 * AclPermission enum, this converter returns null instead of crashing with
 * "No enum constant" IllegalArgumentException.
 */
@Slf4j
@ReadingConverter
public class StringToAclPermissionConverter implements Converter<String, AclPermission> {
    @Override
    public AclPermission convert(String source) {
        try {
            return AclPermission.valueOf(source);
        } catch (IllegalArgumentException e) {
            log.debug("Ignoring unknown AclPermission value from MongoDB: {}", source);
            return null;
        }
    }
}
