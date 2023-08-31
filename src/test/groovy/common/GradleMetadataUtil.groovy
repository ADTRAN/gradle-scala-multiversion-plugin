package common

import org.gradle.api.publish.internal.GradleModuleMetadataWriter
import org.gradle.internal.hash.ChecksumService
import org.gradle.internal.hash.HashCode
import org.gradle.internal.hash.Hashing
import org.gradle.internal.id.UniqueId
import org.gradle.internal.scopeids.id.BuildInvocationScopeId

class GradleMetadataUtil {

    static ChecksumService createChecksumService() {
        new ChecksumService() {

            @Override
            HashCode md5(File file) {
                Hashing.md5().hashBytes(file.name.bytes)
            }

            @Override
            HashCode sha1(File file) {
                Hashing.sha1().hashBytes(file.name.bytes)
            }

            @Override
            HashCode sha256(File file) {
                Hashing.sha256().hashBytes(file.name.bytes)
            }

            @Override
            HashCode sha512(File file) {
                Hashing.sha512().hashBytes(file.name.bytes)
            }

            @Override
            HashCode hash(File src, String algorithm) {
                def algo = algorithm.toLowerCase().replaceAll('-', '')
                Hashing."$algo"().hashBytes(src.name.bytes)
            }
        }
    }

    static GradleModuleMetadataWriter createMetadataWriter() {
        def buildInvocationScopeId = new BuildInvocationScopeId(UniqueId.generate())
        return new GradleModuleMetadataWriter(buildInvocationScopeId, null, createChecksumService())
    }
}