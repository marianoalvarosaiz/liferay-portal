<#if entityColumn.comparator == "=">
	<#if entityColumn.isPrimitiveType(false)>
		<#if stringUtil.equals(entityColumn.type, "boolean")>
			(${entityColumn.name} != ${entity.varName}.is${entityColumn.methodName}())
		<#else>
			(${entityColumn.name} != ${entity.varName}.get${entityColumn.methodName}())
		</#if>
	<#else>
		<#if stringUtil.equals(entityColumn.type, "String") && entityColumn.isConvertNull()>
			!Objects.equals(${entityColumn.name}NullSafe, ${entity.varName}.get${entityColumn.methodName}())
		<#else>
			!Objects.equals(${entityColumn.name}, ${entity.varName}.get${entityColumn.methodName}())
		</#if>
	</#if>
<#elseif entityColumn.comparator == "!=">
	<#if entityColumn.isPrimitiveType(false)>
		<#if stringUtil.equals(entityColumn.type, "boolean")>
			(${entityColumn.name} == ${entity.varName}.is${entityColumn.methodName}())
		<#else>
			(${entityColumn.name} == ${entity.varName}.get${entityColumn.methodName}())
		</#if>
	<#else>
		<#if stringUtil.equals(entityColumn.type, "String") && entityColumn.isConvertNull()>
			Objects.equals(${entityColumn.name}NullSafe, ${entity.varName}.get${entityColumn.methodName}())
		<#else>
			Objects.equals(${entityColumn.name}, ${entity.varName}.get${entityColumn.methodName}())
		</#if>
	</#if>
<#elseif entityColumn.comparator == ">">
	<#if stringUtil.equals(entityColumn.type, "BigDecimal")>
		(${entityColumn.name}.compareTo(${entity.varName}.get${entityColumn.methodName}()) >= 0)
	<#elseif stringUtil.equals(entityColumn.type, "Date")>
		(${entityColumn.name}.getTime() >= ${entity.varName}.get${entityColumn.methodName}().getTime())
	<#elseif stringUtil.equals(entityColumn.type, "String") && entityColumn.isConvertNull()>
		(${entityColumn.name}NullSafe >= ${entity.varName}.get${entityColumn.methodName}())
	<#else>
		(${entityColumn.name} >= ${entity.varName}.get${entityColumn.methodName}())
	</#if>
<#elseif entityColumn.comparator == ">=">
	<#if stringUtil.equals(entityColumn.type, "BigDecimal")>
		(${entityColumn.name}.compareTo(${entity.varName}.get${entityColumn.methodName}()) > 0)
	<#elseif stringUtil.equals(entityColumn.type, "Date")>
		(${entityColumn.name}.getTime() > ${entity.varName}.get${entityColumn.methodName}().getTime())
	<#elseif stringUtil.equals(entityColumn.type, "String") && entityColumn.isConvertNull()>
		(${entityColumn.name}NullSafe > ${entity.varName}.get${entityColumn.methodName}())
	<#else>
		(${entityColumn.name} > ${entity.varName}.get${entityColumn.methodName}())
	</#if>
<#elseif entityColumn.comparator == "<">
	<#if stringUtil.equals(entityColumn.type, "BigDecimal")>
		(${entityColumn.name}.compareTo(${entity.varName}.get${entityColumn.methodName}()) <= 0)
	<#elseif stringUtil.equals(entityColumn.type, "Date")>
		(${entityColumn.name}.getTime() <= ${entity.varName}.get${entityColumn.methodName}().getTime())
	<#elseif stringUtil.equals(entityColumn.type, "String") && entityColumn.isConvertNull()>
		(${entityColumn.name}NullSafe <= ${entity.varName}.get${entityColumn.methodName}())
	<#else>
		(${entityColumn.name} <= ${entity.varName}.get${entityColumn.methodName}())
	</#if>
<#elseif entityColumn.comparator == "<=">
	<#if stringUtil.equals(entityColumn.type, "BigDecimal")>
		(${entityColumn.name}.compareTo(${entity.varName}.get${entityColumn.methodName}()) < 0)
	<#elseif stringUtil.equals(entityColumn.type, "Date")>
		(${entityColumn.name}.getTime() < ${entity.varName}.get${entityColumn.methodName}().getTime())
	<#elseif stringUtil.equals(entityColumn.type, "String") && entityColumn.isConvertNull()>
		(${entityColumn.name}NullSafe < ${entity.varName}.get${entityColumn.methodName}())
	<#else>
		(${entityColumn.name} < ${entity.varName}.get${entityColumn.methodName}())
	</#if>
<#elseif stringUtil.equals(entityColumn.comparator, "LIKE")>
	<#if stringUtil.equals(entityColumn.type, "String") && entityColumn.isConvertNull()>
		!StringUtil.wildcardMatches(${entity.varName}.get${entityColumn.methodName}(), ${entityColumn.name}NullSafe, '_', '%', '\\',
	<#else>
		!StringUtil.wildcardMatches(${entity.varName}.get${entityColumn.methodName}(), ${entityColumn.name}, '_', '%', '\\',
	</#if>
	<#if entityColumn.isCaseSensitive()>
		true
	<#else>
		false
	</#if>
	)
</#if>