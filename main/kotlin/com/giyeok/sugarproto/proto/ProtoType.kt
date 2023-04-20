package com.giyeok.sugarproto.proto

import com.giyeok.sugarproto.SugarProtoAst
import com.giyeok.sugarproto.name.SemanticName

sealed class ProtoType {
  data class StreamType(val valueType: ValueType): ProtoType()
}

sealed class ValueType: ProtoType() {
  data class RepeatedType(val elemType: AtomicType): ValueType()
  data class OptionalType(val elemType: AtomicType): ValueType()
  data class MapType(val keyType: AtomicType.PrimitiveType, val valueType: AtomicType): ValueType()
}

sealed class AtomicType: ValueType() {
  object EmptyType: AtomicType()

  data class PrimitiveType(val type: SugarProtoAst.PrimitiveTypeEnum): AtomicType()

  sealed class EnumRefType: AtomicType() {
    abstract val refName: SemanticName
  }

  sealed class MessageOrSealedRefType: AtomicType() {
    abstract val refName: SemanticName
  }

  sealed class MessageRefType: MessageOrSealedRefType()

  sealed class SealedRefType: MessageOrSealedRefType()

  // 이름은 항상 루트 scope에서부터 시작하는 canonical name으로
  data class UnknownName(val name: String): AtomicType()

  data class MessageName(val name: SemanticName): MessageRefType() {
    override val refName get() = name
  }

  data class EnumName(val name: SemanticName): EnumRefType() {
    override val refName get() = name
  }

  data class SealedName(val name: SemanticName): SealedRefType() {
    override val refName get() = name
  }

  // generated name은 별도로 처리
  // 역시 항상 루트 scope에서 시작하는 canonical name
  data class GeneratedMessageName(val name: SemanticName): MessageRefType() {
    override val refName get() = name
  }

  data class GeneratedEnumName(val name: SemanticName): EnumRefType() {
    override val refName get() = name
  }

  data class GeneratedSealedName(val name: SemanticName): SealedRefType() {
    override val refName get() = name
  }
}
