package org.omg.DynamicAny;


/**
* org/omg/DynamicAny/DynFixed.java .
* Generated by the IDL-to-Java compiler (portable), version "3.2"
* from /scratch/jenkins/workspace/8-2-build-olinux-amd64/jdk8u41/500/corba/src/share/classes/org/omg/DynamicAny/DynamicAny.idl
* Tuesday, January 14, 2020 11:00:47 PM PST
*/


/**
    * DynFixed objects support the manipulation of IDL fixed values.
    * Because IDL does not have a generic type that can represent fixed types with arbitrary
    * number of digits and arbitrary scale, the operations use the IDL string type.
    */
public interface DynFixed extends DynFixedOperations, org.omg.DynamicAny.DynAny, org.omg.CORBA.portable.IDLEntity 
{
} // interface DynFixed
