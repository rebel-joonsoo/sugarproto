package com.giyeok.sugarproto.mutkt

import com.giyeok.sugarproto.name.SemanticName
import com.giyeok.sugarproto.proto.AtomicType
import com.giyeok.sugarproto.proto.ValueType

class ProtoConversionExprGen(
  val tsGen: TypeStringGen,
  val protoOuterClassName: String,
) {
  // atomic = prim, enum, message/sealed, empty
  // prim:
  // fromProto(proto): <fieldName> = proto.<fieldName>
  // toProto(builder): builder.<fieldName> = this.<fieldName>
  // enum:
  // fromProto(proto): <fieldName> = <TypeName>.fromProto(proto.<fieldName>)
  // toProto(builder): builder.<fieldName> = this.<fieldName>.toProto()
  // message/sealed:
  // fromProto(proto): <fieldName> = <TypeName>.fromProto(proto.<fieldName>)
  // toProto(builder): this.<fieldName>.toProto(builder.<fieldName>Builder)
  // empty: unsupported

  // optional<atomic>:
  // fromProto(proto): <fieldName> = if (!proto.has<FieldName>()) null else <<fromProto(elem)>>
  // toProto(builder):
  // prim: this.<fieldName>?.let { value -> builder.<fieldName> = value } ?: builder.clear<FieldName>()
  // enum: this.<fieldName>?.let { value -> builder.<fieldName> = value.toProto() } ?: builder.clear<FieldName>()
  // msgs: this.<fieldName>?.toProto(builder.<fieldName>Builder) ?: builder.clear<FieldName>()

  // repeated<atomic>:
  // fromProto(proto): <fieldName> = GdxArray(proto.<fieldName>Count)
  // post: proto.<fieldName>List.forEach { elem ->
  // prim:   instance.<fieldName>.add(elem)
  // enum:   instance.<fieldName>.add(<TypeName>.fromProto(elem))
  // msgs:   instance.<fieldName>.add(<TypeName>.fromProto(elem))
  //       }
  // toProto(builder): this.<fieldName>List.forEach { elem ->
  // prim:               builder.add<FieldName>(elem)
  // enum:               builder.add<FieldName>(elem.toProto())
  // msgs:               elem.toProto(builder.add<FieldName>Builder())
  //                   }

  // map<prim, atomic>:
  // fromProto(proto): <fieldName> = GdxIntMap(proto.<fieldName>Count)
  // post: proto.<fieldName>Map.forEach { entry ->
  // prim:   instance.<fieldName>.put(entry.key, entry.value)
  // enum:   instance.<fieldName>.put(entry.key, <TypeName>.fromProto(entry.value))
  // msgs:   instance.<fieldName>.put(entry.key, <TypeName>.fromProto(entry.value))
  //       }
  // toProto(builder): this.<fieldName>Map.forEach { entry ->
  // prim:               builder.put<FieldName>(entry.key, entry.value)
  // enum:               builder.put<FieldName>(entry.key, entry.value.toProto())
  // msgs:               val valueBuilder = <ProtoTypeName>.newBuilder()
  //                     entry.value.toProto(valueBuilder)
  //                     builder.put<FieldName>(entry.key, valueBuilder.build())
  //                   }

  fun fromField(fieldDef: KtFieldDef, collectionSizeHint: String? = null): ProtoConversionExpr {
    return when (fieldDef.type) {
      AtomicType.EmptyType, is AtomicType.UnknownName ->
        throw IllegalStateException("Unsupported? maybe? ${fieldDef.type}")

      is AtomicType.PrimitiveType ->
        ProtoConversionExpr(
          FromProtoExpr.PrimFromProto(fieldDef.name),
          null,
          ToProtoExpr.PrimToProto(fieldDef.name),
        )

      is AtomicType.EnumRefType ->
        ProtoConversionExpr(
          FromProtoExpr.EnumFromProto(fieldDef.name, fieldDef.type.refName),
          null,
          ToProtoExpr.EnumToProto(fieldDef.name),
        )

      is AtomicType.MessageRefType ->
        ProtoConversionExpr(
          FromProtoExpr.MessageFromProto(fieldDef.name, fieldDef.type.refName),
          null,
          ToProtoExpr.MessageToProto(fieldDef.name),
        )

      is ValueType.OptionalType -> {
        when (val elemType = fieldDef.type.elemType) {
          AtomicType.EmptyType, is AtomicType.UnknownName -> TODO()

          is AtomicType.PrimitiveType ->
            ProtoConversionExpr(
              FromProtoExpr.OptionalFromProto(
                fieldDef.name,
                FromProtoExpr.PrimFromProto(fieldDef.name)
              ),
              null,
              ToProtoExpr.PrimOptionalToProto(fieldDef.name),
            )

          is AtomicType.EnumRefType ->
            ProtoConversionExpr(
              FromProtoExpr.OptionalFromProto(
                fieldDef.name,
                FromProtoExpr.EnumFromProto(fieldDef.name, elemType.refName)
              ),
              null,
              ToProtoExpr.EnumOptionalToProto(fieldDef.name),
            )

          is AtomicType.MessageRefType ->
            ProtoConversionExpr(
              FromProtoExpr.OptionalFromProto(
                fieldDef.name,
                FromProtoExpr.MessageFromProto(fieldDef.name, elemType.refName)
              ),
              null,
              ToProtoExpr.MessageOptionalToProto(fieldDef.name)
            )
        }
      }

      is ValueType.RepeatedType -> {
        val ts = tsGen.fromType(fieldDef.type, collectionSizeHint)
        when (val elemType = fieldDef.type.elemType) {
          AtomicType.EmptyType, is AtomicType.UnknownName -> TODO()

          is AtomicType.PrimitiveType ->
            ProtoConversionExpr(
              FromProtoExpr.Const(ts.defaultValue),
              FromProtoPostProcessExpr.ForEachRepeated(
                fieldDef.name,
                FromProtoPostProcessExpr.PrimValueGetterExpr
              ),
              ToProtoExpr.ForEachRepeated(fieldDef.name, ElemType.PRIM, "")
            )

          is AtomicType.EnumRefType ->
            ProtoConversionExpr(
              FromProtoExpr.Const(ts.defaultValue),
              FromProtoPostProcessExpr.ForEachRepeated(
                fieldDef.name,
                FromProtoPostProcessExpr.EnumValueGetterExpr(elemType.refName)
              ),
              ToProtoExpr.ForEachRepeated(fieldDef.name, ElemType.ENUM, "")
            )

          is AtomicType.MessageRefType -> {
            val protoTypeName = "$protoOuterClassName${elemType.refName.className}"
            ProtoConversionExpr(
              FromProtoExpr.Const(ts.defaultValue),
              FromProtoPostProcessExpr.ForEachRepeated(
                fieldDef.name,
                FromProtoPostProcessExpr.MessageValueGetterExpr(elemType.refName)
              ),
              ToProtoExpr.ForEachRepeated(fieldDef.name, ElemType.MESSAGE, protoTypeName)
            )
          }
        }
      }

      is ValueType.MapType -> {
        val ts = tsGen.fromType(fieldDef.type, collectionSizeHint)
        when (val elemType = fieldDef.type.valueType) {
          AtomicType.EmptyType, is AtomicType.UnknownName -> TODO()

          is AtomicType.PrimitiveType ->
            ProtoConversionExpr(
              FromProtoExpr.Const(ts.defaultValue),
              FromProtoPostProcessExpr.ForEachMap(
                fieldDef.name,
                FromProtoPostProcessExpr.PrimValueGetterExpr,
              ),
              ToProtoExpr.ForEachMap(fieldDef.name, ElemType.PRIM, "")
            )

          is AtomicType.EnumRefType ->
            ProtoConversionExpr(
              FromProtoExpr.Const(ts.defaultValue),
              FromProtoPostProcessExpr.ForEachMap(
                fieldDef.name,
                FromProtoPostProcessExpr.EnumValueGetterExpr(elemType.refName),
              ),
              ToProtoExpr.ForEachMap(fieldDef.name, ElemType.ENUM, "")
            )

          is AtomicType.MessageRefType -> {
            val protoTypeName = "$protoOuterClassName${elemType.refName.className}"
            ProtoConversionExpr(
              FromProtoExpr.Const(ts.defaultValue),
              FromProtoPostProcessExpr.ForEachMap(
                fieldDef.name,
                FromProtoPostProcessExpr.MessageValueGetterExpr(elemType.refName),
              ),
              ToProtoExpr.ForEachMap(fieldDef.name, ElemType.MESSAGE, protoTypeName)
            )
          }
        }
      }
    }
  }
}

data class ProtoConversionExpr(
  val fromProtoExpr: FromProtoExpr,
  val fromProtoPostProcessExpr: FromProtoPostProcessExpr?,
  val toProtoExpr: ToProtoExpr,
)

sealed class FromProtoExpr {
  abstract fun expr(protoExpr: String): String

  sealed class AtomicFromProtoExpr: FromProtoExpr()

  data class PrimFromProto(val fieldName: SemanticName): AtomicFromProtoExpr() {
    override fun expr(protoExpr: String): String =
      "$protoExpr.${fieldName.classFieldName}"
  }

  data class EnumFromProto(val fieldName: SemanticName, val enumTypeName: SemanticName):
    AtomicFromProtoExpr() {
    override fun expr(protoExpr: String): String =
      "${enumTypeName.enumClassName}.fromProto($protoExpr.${fieldName.classFieldName})"
  }

  data class MessageFromProto(val fieldName: SemanticName, val messageTypeName: SemanticName):
    AtomicFromProtoExpr() {
    override fun expr(protoExpr: String): String =
      "${messageTypeName.className}.fromProto($protoExpr.${fieldName.classFieldName})"
  }

  data class OptionalFromProto(val fieldName: SemanticName, val fromProtoExpr: AtomicFromProtoExpr):
    FromProtoExpr() {
    override fun expr(protoExpr: String): String {
      val fromProto = fromProtoExpr.expr(protoExpr)
      return "if (!$protoExpr.has${fieldName.capitalClassFieldName}()) null else $fromProto"
    }
  }

  data class Const(val const: String): FromProtoExpr() {
    override fun expr(protoExpr: String): String = const
  }
}

sealed class FromProtoPostProcessExpr {
  abstract fun generate(gen: MutableKtDataClassGen, protoExpr: String, instanceExpr: String)

  sealed class AtomicValueGetterExpr {
    abstract fun expr(elemExpr: String): String
  }

  object PrimValueGetterExpr: AtomicValueGetterExpr() {
    override fun expr(elemExpr: String): String = elemExpr
  }

  data class EnumValueGetterExpr(val typeName: SemanticName): AtomicValueGetterExpr() {
    override fun expr(elemExpr: String): String = "${typeName.enumClassName}.fromProto($elemExpr)"
  }

  data class MessageValueGetterExpr(val typeName: SemanticName): AtomicValueGetterExpr() {
    override fun expr(elemExpr: String): String = "${typeName.className}.fromProto($elemExpr)"
  }

  data class ForEachRepeated(val fieldName: SemanticName, val valueGetter: AtomicValueGetterExpr):
    FromProtoPostProcessExpr() {
    override fun generate(gen: MutableKtDataClassGen, protoExpr: String, instanceExpr: String) {
      gen.addLine("$protoExpr.${fieldName.classFieldName}List.forEach { elem ->")
      gen.indent {
        val getter = valueGetter.expr("elem")
        gen.addLine("$instanceExpr.${fieldName.classFieldName}.add($getter)")
      }
      gen.addLine("}")
    }
  }

  data class ForEachMap(val fieldName: SemanticName, val valueGetter: AtomicValueGetterExpr):
    FromProtoPostProcessExpr() {
    override fun generate(gen: MutableKtDataClassGen, protoExpr: String, instanceExpr: String) {
      gen.addLine("$protoExpr.${fieldName.classFieldName}Map.forEach { entry ->")
      gen.indent {
        val getter = valueGetter.expr("entry.value")
        gen.addLine("$instanceExpr.${fieldName.classFieldName}.put(entry.key, $getter)")
      }
      gen.addLine("}")
    }
  }
}

sealed class ToProtoExpr {
  abstract fun generate(gen: MutableKtDataClassGen, thisExpr: String, builderExpr: String)

  data class PrimToProto(val fieldName: SemanticName): ToProtoExpr() {
    override fun generate(gen: MutableKtDataClassGen, thisExpr: String, builderExpr: String) {
      gen.addLine("$builderExpr.${fieldName.classFieldName} = $thisExpr.${fieldName.classFieldName}")
    }
  }

  data class EnumToProto(val fieldName: SemanticName): ToProtoExpr() {
    override fun generate(gen: MutableKtDataClassGen, thisExpr: String, builderExpr: String) {
      gen.addLine("$builderExpr.${fieldName.classFieldName} = $thisExpr.${fieldName.classFieldName}.toProto()")
    }
  }

  data class MessageToProto(val fieldName: SemanticName): ToProtoExpr() {
    override fun generate(gen: MutableKtDataClassGen, thisExpr: String, builderExpr: String) {
      gen.addLine("$thisExpr.${fieldName.classFieldName}.toProto($builderExpr.${fieldName.classFieldName}Builder)")
    }
  }

  data class PrimOptionalToProto(val fieldName: SemanticName): ToProtoExpr() {
    override fun generate(gen: MutableKtDataClassGen, thisExpr: String, builderExpr: String) {
      gen.addLine("$thisExpr.${fieldName.classFieldName}?.let { value -> $builderExpr.${fieldName.classFieldName} = value } ?: $builderExpr.clear${fieldName.capitalClassFieldName}()")
    }
  }

  data class EnumOptionalToProto(val fieldName: SemanticName): ToProtoExpr() {
    override fun generate(gen: MutableKtDataClassGen, thisExpr: String, builderExpr: String) {
      gen.addLine("$thisExpr.${fieldName.classFieldName}?.let { value -> $builderExpr.${fieldName.classFieldName} = value.toProto() } ?: $builderExpr.clear${fieldName.capitalClassFieldName}()")
    }
  }

  data class MessageOptionalToProto(val fieldName: SemanticName): ToProtoExpr() {
    override fun generate(gen: MutableKtDataClassGen, thisExpr: String, builderExpr: String) {
      gen.addLine("$thisExpr.${fieldName.classFieldName}?.toProto($builderExpr.${fieldName.classFieldName}Builder) ?: $builderExpr.clear${fieldName.capitalClassFieldName}()")
    }
  }

  data class ForEachRepeated(
    val fieldName: SemanticName,
    val elemType: ElemType,
    val messageProtoName: String
  ): ToProtoExpr() {
    override fun generate(gen: MutableKtDataClassGen, thisExpr: String, builderExpr: String) {
      gen.addLine("$thisExpr.${fieldName.classFieldName}.forEach { elem ->")
      gen.indent {
        when (elemType) {
          ElemType.PRIM ->
            gen.addLine("$builderExpr.add${fieldName.capitalClassFieldName}(elem)")

          ElemType.ENUM ->
            gen.addLine("$builderExpr.add${fieldName.capitalClassFieldName}(elem.toProto())")

          ElemType.MESSAGE ->
            gen.addLine("elem.toProto($builderExpr.add${fieldName.capitalClassFieldName}Builder())")
        }
      }
      gen.addLine("}")
    }
  }

  data class ForEachMap(
    val fieldName: SemanticName,
    val elemType: ElemType,
    val messageProtoClassName: String
  ): ToProtoExpr() {
    override fun generate(gen: MutableKtDataClassGen, thisExpr: String, builderExpr: String) {
      gen.addLine("$thisExpr.${fieldName.classFieldName}.forEach { (key, value) ->")
      gen.indent {
        when (elemType) {
          ElemType.PRIM ->
            gen.addLine("$builderExpr.put${fieldName.capitalClassFieldName}(key, value)")

          ElemType.ENUM ->
            gen.addLine("$builderExpr.put${fieldName.capitalClassFieldName}(key, value.toProto())")

          ElemType.MESSAGE -> {
            gen.addLine("val valueBuilder = $messageProtoClassName.newBuilder()")
            gen.addLine("value.toProto(valueBuilder)")
            gen.addLine("$builderExpr.put${fieldName.capitalClassFieldName}(key, valueBuilder.build())")
          }
        }
      }
      gen.addLine("}")
    }
  }
}

enum class ElemType {
  PRIM, ENUM, MESSAGE
}
