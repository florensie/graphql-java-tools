package graphql.kickstart.tools

import graphql.kickstart.tools.resolver.FieldResolverScanner
import graphql.kickstart.tools.util.GraphQLRootResolver
import graphql.kickstart.tools.util.JavaType
import org.apache.commons.lang3.reflect.TypeUtils

internal abstract class ResolverInfo {
    abstract fun getFieldSearches(): List<FieldResolverScanner.Search>

    fun getRealResolverClass(resolver: GraphQLResolver<*>, options: SchemaParserOptions) =
        options.proxyHandlers.find { it.canHandle(resolver) }
            ?.getTargetClass(resolver)
            ?: resolver.javaClass
}

internal interface DataClassTypeResolverInfo {
    val dataClassType: Class<out Any>
}

internal class NormalResolverInfo(
    val resolver: GraphQLResolver<*>,
    options: SchemaParserOptions
) : DataClassTypeResolverInfo, ResolverInfo() {

    val resolverType = getRealResolverClass(resolver, options)
    override val dataClassType = findDataClass()

    private fun findDataClass(): Class<out Any> {
        val type = TypeUtils.getTypeArguments(resolverType, GraphQLResolver::class.java)[GraphQLResolver::class.java.typeParameters[0]]

        if (type == null || type !is Class<*>) {
            throw ResolverError("Unable to determine data class for resolver '${resolverType.name}' from generic interface! This is most likely a bug with graphql-java-tools.")
        }

        if (type == Void::class.java) {
            throw ResolverError("Resolvers may not have ${Void::class.java.name} as their type, use a real type or use a root resolver interface.")
        }

        return type
    }

    override fun getFieldSearches(): List<FieldResolverScanner.Search> {
        return listOf(
            FieldResolverScanner.Search(resolverType, this, resolver, dataClassType),
            FieldResolverScanner.Search(dataClassType, this, null)
        )
    }
}


internal class MultiResolverInfo(val resolverInfoList: List<NormalResolverInfo>,
                                 override val dataClassType: Class<out Any>
) : DataClassTypeResolverInfo, ResolverInfo() {

    override fun getFieldSearches(): List<FieldResolverScanner.Search> {
        return resolverInfoList
            .asSequence()
            .map { FieldResolverScanner.Search(it.resolverType, this, it.resolver, dataClassType) }
            .plus(FieldResolverScanner.Search(dataClassType, this, null))
            .toList()
    }
}

internal class RootResolverInfo(
    val resolvers: List<GraphQLRootResolver>,
    private val options: SchemaParserOptions
) : ResolverInfo() {
    override fun getFieldSearches() =
        resolvers.map { FieldResolverScanner.Search(getRealResolverClass(it, options), this, it) }
}

internal class DataClassResolverInfo(private val dataClass: JavaType) : ResolverInfo() {
    override fun getFieldSearches() =
        listOf(FieldResolverScanner.Search(dataClass, this, null))
}

internal class MissingResolverInfo : ResolverInfo() {
    override fun getFieldSearches(): List<FieldResolverScanner.Search> = listOf()
}

internal class ResolverError(message: String, cause: Throwable? = null) : RuntimeException(message, cause)
