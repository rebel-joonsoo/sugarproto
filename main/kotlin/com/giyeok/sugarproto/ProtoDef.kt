package com.giyeok.sugarproto

sealed class ProtoDef

data class ProtoMessageDef(
  val name: String,
  val members: List<ProtoMessageMember>,
): ProtoDef()

data class ProtoStreamableFieldType(
  val isStream: Boolean,
  val valueType: ProtoFieldType,
)

data class ProtoFieldType(
  val kind: FieldKindEnum,
  val optional: Boolean,
  val repeated: Boolean,
  val type: String,
)

enum class FieldKindEnum {
  PrimitiveKind,

  // message or enum
  MessageKind,

  MapKind,
}

sealed class ProtoMessageMember {
  data class ProtoFieldDef(
    val type: ProtoFieldType,
    val name: String,
    val tag: SugarProtoAst.IntLiteral,
    val options: SugarProtoAst.FieldOptions?,
  ): ProtoMessageMember()

  data class ProtoOneOf(
    val name: String,
    val members: List<ProtoOneOfMember>,
  ): ProtoMessageMember()

  sealed class ProtoOneOfMember {
    data class OneOfField(val field: ProtoFieldDef): ProtoOneOfMember()
    data class OneOfOption(val option: SugarProtoAst.OptionDef): ProtoOneOfMember()
  }

  data class ProtoNestedEnumDef(
    val enum: ProtoEnumDef
  ): ProtoMessageMember()

  data class ProtoNestedMessageDef(
    val message: ProtoMessageDef
  ): ProtoMessageMember()

  data class ProtoMessageOptionDef(
    val optionDef: SugarProtoAst.OptionDef
  ): ProtoMessageMember()

  data class ProtoReservedDef(
    val reserved: List<SugarProtoAst.ReservedItem>
  ): ProtoMessageMember()
}

data class ProtoEnumDef(
  val name: String,
  val members: List<ProtoEnumMember>,
): ProtoDef()

sealed class ProtoEnumMember {
  data class EnumOption(
    val optionDef: SugarProtoAst.OptionDef
  ): ProtoEnumMember()

  data class EnumValueDef(
    val minusTag: Boolean,
    val tag: SugarProtoAst.IntLiteral,
    val name: String,
    val options: SugarProtoAst.FieldOptions?,
  ): ProtoEnumMember()
}

data class ProtoServiceDef(
  val name: String,
  val members: List<ServiceMember>,
): ProtoDef()

sealed class ServiceMember {
  data class ServiceOption(
    val optionDef: SugarProtoAst.OptionDef
  ): ServiceMember()

  data class ProtoRpcDef(
    val name: String,
    val isInTypeStream: Boolean,
    val inType: String,
    val isOutTypeStream: Boolean,
    val outType: String,
    val options: SugarProtoAst.FieldOptions?,
  ): ServiceMember()
}
